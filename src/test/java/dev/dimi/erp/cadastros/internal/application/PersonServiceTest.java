package dev.dimi.erp.cadastros.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.ModalityActivatedEvent;
import dev.dimi.erp.cadastros.ModalityDeactivatedEvent;
import dev.dimi.erp.cadastros.PersonDeactivatedEvent;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonReactivatedEvent;
import dev.dimi.erp.cadastros.PersonRegisteredEvent;
import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.cadastros.PersonUpdatedEvent;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityAssignment;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityId;
import dev.dimi.erp.cadastros.internal.persistence.PersonModalityRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.documents.InvalidCpfException;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

  @Mock private PersonRepository persons;
  @Mock private PersonModalityRepository modalities;
  @Mock private dev.dimi.erp.cadastros.internal.persistence.PersonAddressRepository addresses;
  @Mock private dev.dimi.erp.cadastros.internal.persistence.PersonContactRepository contacts;
  @Mock private ApplicationEventPublisher events;

  private PersonService service;

  private static final UserId ACTOR = UserId.newId();
  private static final String CPF_VALIDO = "11144477735";
  private static final String CNPJ_VALIDO = "11222333000181";

  @BeforeEach
  void setUp() {
    service = new PersonService(persons, modalities, addresses, contacts, events);
    TenantContext.set(new Tenant(UUID.randomUUID(), "demo", "tenant_demo"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("register")
  class Register {

    @Test
    void registrarPfValido_comUmaModalidade_publicaEventosCorretos() {
      given(persons.existsByCpf(CPF_VALIDO)).willReturn(false);
      given(persons.save(any(Person.class))).willAnswer(inv -> inv.getArgument(0));
      given(modalities.save(any(PersonModalityAssignment.class)))
          .willAnswer(inv -> inv.getArgument(0));

      RegisterPersonCommand cmd =
          new RegisterPersonCommand(
              PersonType.PF,
              "João Almeida",
              "João",
              CPF_VALIDO,
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              Set.of(Modality.CUSTOMER),
              ACTOR);

      PersonSummary result = service.register(cmd);

      assertThat(result.type()).isEqualTo(PersonType.PF);
      assertThat(result.cpf()).isEqualTo(CPF_VALIDO);
      assertThat(result.active()).isTrue();
      assertThat(result.activeModalities()).containsExactly(Modality.CUSTOMER);
      verify(events).publishEvent(any(PersonRegisteredEvent.class));
      verify(events).publishEvent(any(ModalityActivatedEvent.class));
    }

    @Test
    void registrarPfComCpfRepetido_lancaConflict() {
      given(persons.existsByCpf(CPF_VALIDO)).willReturn(true);

      RegisterPersonCommand cmd =
          new RegisterPersonCommand(
              PersonType.PF,
              "João",
              null,
              CPF_VALIDO,
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              Set.of(Modality.CUSTOMER),
              ACTOR);

      assertThatThrownBy(() -> service.register(cmd))
          .isInstanceOf(PersonAlreadyExistsException.class)
          .hasFieldOrPropertyWithValue("code", "cadastros.person-already-exists");

      verify(persons, never()).save(any());
    }

    @Test
    void registrarPfComCpfConsumidorFinal_permiteDuplicata() {
      given(persons.save(any(Person.class))).willAnswer(inv -> inv.getArgument(0));
      given(modalities.save(any(PersonModalityAssignment.class)))
          .willAnswer(inv -> inv.getArgument(0));

      RegisterPersonCommand cmd =
          new RegisterPersonCommand(
              PersonType.PF,
              "Consumidor Final",
              null,
              "00000000000",
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              Set.of(Modality.CUSTOMER),
              ACTOR);

      PersonSummary result = service.register(cmd);

      assertThat(result.cpf()).isEqualTo("00000000000");
      // não consultamos existsByCpf na sentinela — RN-CAD-001
      verify(persons, never()).existsByCpf("00000000000");
    }

    @Test
    void registrarPfSemCpf_lancaInvalidState() {
      RegisterPersonCommand cmd =
          new RegisterPersonCommand(
              PersonType.PF,
              "João",
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              Set.of(Modality.CUSTOMER),
              ACTOR);

      assertThatThrownBy(() -> service.register(cmd))
          .isInstanceOf(InvalidPersonStateException.class)
          .hasFieldOrPropertyWithValue("code", "cadastros.invalid-person-state");
    }

    @Test
    void registrarPjComCpf_lancaInvalidState() {
      RegisterPersonCommand cmd =
          new RegisterPersonCommand(
              PersonType.PJ,
              "Empresa Exemplo Ltda",
              null,
              CPF_VALIDO,
              CNPJ_VALIDO,
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              Set.of(Modality.SUPPLIER),
              ACTOR);

      assertThatThrownBy(() -> service.register(cmd))
          .isInstanceOf(InvalidPersonStateException.class);
    }

    @Test
    void registrarPfComCpfInvalido_propagaDoValueObject() {
      RegisterPersonCommand cmd =
          new RegisterPersonCommand(
              PersonType.PF,
              "João",
              null,
              "11144477736", // DV errado
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              Set.of(Modality.CUSTOMER),
              ACTOR);

      assertThatThrownBy(() -> service.register(cmd)).isInstanceOf(InvalidCpfException.class);
    }
  }

  @Nested
  @DisplayName("deactivate")
  class Deactivate {

    @Test
    void pessoaAtiva_passaParaInativa_ePublicaEvento() {
      Person p = newActivePerson();
      given(persons.findById(p.id().value())).willReturn(Optional.of(p));
      given(modalities.findByPersonId(p.id().value())).willReturn(List.of());

      PersonSummary result = service.deactivate(p.id(), "encerramento", ACTOR);

      assertThat(result.active()).isFalse();
      assertThat(result.deactivatedAt()).isNotNull();
      verify(events).publishEvent(any(PersonDeactivatedEvent.class));
    }

    @Test
    void pessoaJaInativa_lancaInvalidState() {
      Person p = newActivePerson();
      p.deactivate();
      PersonId id = p.id();
      given(persons.findById(id.value())).willReturn(Optional.of(p));

      assertThatThrownBy(() -> service.deactivate(id, "motivo", ACTOR))
          .isInstanceOf(InvalidPersonStateException.class);
    }

    @Test
    void pessoaInexistente_lancaNotFound() {
      PersonId target = PersonId.newId();
      given(persons.findById(target.value())).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.deactivate(target, "motivo", ACTOR))
          .isInstanceOf(PersonNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("reactivate")
  class Reactivate {

    @Test
    void pessoaInativa_voltaParaAtiva() {
      Person p = newActivePerson();
      p.deactivate();
      given(persons.findById(p.id().value())).willReturn(Optional.of(p));
      given(modalities.findByPersonId(p.id().value())).willReturn(List.of());

      PersonSummary result = service.reactivate(p.id(), ACTOR);

      assertThat(result.active()).isTrue();
      verify(events).publishEvent(any(PersonReactivatedEvent.class));
    }

    @Test
    void pessoaJaAtiva_recusa() {
      Person p = newActivePerson();
      PersonId id = p.id();
      given(persons.findById(id.value())).willReturn(Optional.of(p));

      assertThatThrownBy(() -> service.reactivate(id, ACTOR))
          .isInstanceOf(InvalidPersonStateException.class);
    }
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    void mudancaDeTradeName_geraEventoComCampoAlterado() {
      Person p = newActivePerson();
      given(persons.findById(p.id().value())).willReturn(Optional.of(p));
      given(modalities.findByPersonId(p.id().value())).willReturn(List.of());

      UpdatePersonCommand cmd =
          new UpdatePersonCommand(
              p.id(),
              null,
              "Novo Trade",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              ACTOR);

      service.update(cmd);

      assertThat(p.getTradeName()).isEqualTo("Novo Trade");
      verify(events, atLeastOnce()).publishEvent(any(PersonUpdatedEvent.class));
    }

    @Test
    void payloadIdentico_naoGeraEvento() {
      Person p = newActivePerson();
      given(persons.findById(p.id().value())).willReturn(Optional.of(p));
      given(modalities.findByPersonId(p.id().value())).willReturn(List.of());

      UpdatePersonCommand cmd =
          new UpdatePersonCommand(
              p.id(),
              p.getLegalName(),
              p.getTradeName(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              ACTOR);

      service.update(cmd);

      verify(events, never()).publishEvent(any(PersonUpdatedEvent.class));
    }
  }

  @Nested
  @DisplayName("modalidades")
  class Modalidades {

    @Test
    void ativarModalidadeNova_persisteEPublica() {
      Person p = newActivePerson();
      given(persons.findById(p.id().value())).willReturn(Optional.of(p));
      given(modalities.findById(new PersonModalityId(p.id().value(), Modality.SUPPLIER)))
          .willReturn(Optional.empty());
      given(modalities.findByPersonId(p.id().value())).willReturn(List.of());

      service.activateModality(p.id(), Modality.SUPPLIER, ACTOR);

      verify(modalities, times(1)).save(any(PersonModalityAssignment.class));
      verify(events).publishEvent(any(ModalityActivatedEvent.class));
    }

    @Test
    void ativarModalidadeJaAtiva_recusa() {
      Person p = newActivePerson();
      PersonId id = p.id();
      PersonModalityAssignment active = PersonModalityAssignment.activate(id, Modality.CUSTOMER);
      given(persons.findById(id.value())).willReturn(Optional.of(p));
      given(modalities.findById(new PersonModalityId(id.value(), Modality.CUSTOMER)))
          .willReturn(Optional.of(active));

      assertThatThrownBy(() -> service.activateModality(id, Modality.CUSTOMER, ACTOR))
          .isInstanceOf(ModalityAlreadyActiveException.class);

      verify(modalities, never()).save(any());
    }

    @Test
    void desativarModalidadeNaoAtiva_lancaNotActive() {
      Person p = newActivePerson();
      PersonId id = p.id();
      given(persons.findById(id.value())).willReturn(Optional.of(p));
      given(modalities.findById(new PersonModalityId(id.value(), Modality.SUPPLIER)))
          .willReturn(Optional.empty());

      assertThatThrownBy(() -> service.deactivateModality(id, Modality.SUPPLIER, "motivo", ACTOR))
          .isInstanceOf(ModalityNotActiveException.class);
    }

    @Test
    void desativarModalidadeAtiva_marcaInativaEPublica() {
      Person p = newActivePerson();
      PersonModalityAssignment active =
          PersonModalityAssignment.activate(p.id(), Modality.CUSTOMER);
      given(persons.findById(p.id().value())).willReturn(Optional.of(p));
      given(modalities.findById(new PersonModalityId(p.id().value(), Modality.CUSTOMER)))
          .willReturn(Optional.of(active));
      given(modalities.findByPersonId(p.id().value())).willReturn(List.of(active));

      service.deactivateModality(p.id(), Modality.CUSTOMER, "saiu", ACTOR);

      assertThat(active.isActive()).isFalse();
      verify(events).publishEvent(any(ModalityDeactivatedEvent.class));
    }
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private static Person newActivePerson() {
    return Person.register(
        PersonType.PF,
        "João Almeida",
        "João",
        CPF_VALIDO,
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        null,
        null);
  }
}
