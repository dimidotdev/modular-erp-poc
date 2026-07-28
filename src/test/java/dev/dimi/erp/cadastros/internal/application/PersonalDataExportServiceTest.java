package dev.dimi.erp.cadastros.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.cadastros.internal.domain.Employee;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.domain.PersonAddress;
import dev.dimi.erp.cadastros.internal.domain.PersonContact;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityAssignment;
import dev.dimi.erp.cadastros.internal.persistence.EmployeeRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonAddressRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonContactRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonModalityRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalDataExportServiceTest {

  private static final UserId ACTOR = UserId.newId();
  private static final String CPF = "11144477735";

  @Mock private PersonRepository persons;
  @Mock private PersonModalityRepository modalities;
  @Mock private PersonAddressRepository addresses;
  @Mock private PersonContactRepository contacts;
  @Mock private EmployeeRepository employees;

  private PersonalDataExportService service;

  @BeforeEach
  void setUp() {
    service = new PersonalDataExportService(persons, modalities, addresses, contacts, employees);
  }

  @Test
  @DisplayName("pessoa inexistente lança PersonNotFoundException")
  void pessoaInexistente() {
    PersonId id = new PersonId(java.util.UUID.randomUUID());
    given(persons.findById(id.value())).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.export(id, ACTOR)).isInstanceOf(PersonNotFoundException.class);
  }

  @Test
  @DisplayName("agrega person + modalidades + addresses + contacts + employments")
  void agregaTudo() {
    Person person =
        Person.register(
            PersonType.PF,
            "João Para Exportar",
            "João",
            CPF,
            null,
            null,
            null,
            null,
            false,
            LocalDate.of(1980, 1, 1),
            null,
            null,
            null,
            null);
    PersonId id = person.id();

    PersonAddress address =
        PersonAddress.create(
            id.value(),
            java.util.UUID.randomUUID(),
            "01310100",
            "Avenida Paulista",
            "1000",
            null,
            "Bela Vista",
            "São Paulo",
            "SP",
            "BR",
            null,
            true);

    PersonContact contact =
        PersonContact.create(
            id.value(), java.util.UUID.randomUUID(), "joao@example.com", "Pessoal", true);

    BranchId branchId = new BranchId(java.util.UUID.randomUUID());
    Employee employee =
        Employee.hire(
            id.value(),
            branchId.value(),
            java.util.UUID.randomUUID(),
            java.util.UUID.randomUUID(),
            BigDecimal.valueOf(5000),
            LocalDate.of(2024, 1, 15),
            null);

    given(persons.findById(id.value())).willReturn(Optional.of(person));
    given(modalities.findByPersonId(id.value()))
        .willReturn(List.of(PersonModalityAssignment.activate(id, Modality.CUSTOMER)));
    given(addresses.findByPersonId(id.value())).willReturn(List.of(address));
    given(contacts.findByPersonId(id.value())).willReturn(List.of(contact));
    given(employees.findByPersonId(id.value())).willReturn(List.of(employee));

    PersonalDataExport export = service.export(id, ACTOR);

    assertThat(export.person().legalName()).isEqualTo("João Para Exportar");
    assertThat(export.person().cpf()).isEqualTo(CPF);
    assertThat(export.person().activeModalities()).containsExactly(Modality.CUSTOMER);
    assertThat(export.addresses()).hasSize(1);
    assertThat(export.addresses().get(0).street()).isEqualTo("Avenida Paulista");
    assertThat(export.contacts()).hasSize(1);
    assertThat(export.contacts().get(0).value()).isEqualTo("joao@example.com");
    assertThat(export.employments()).hasSize(1);
    assertThat(export.employments().get(0).branchId()).isEqualTo(branchId);
    assertThat(export.exportedBy()).isEqualTo(ACTOR);
    assertThat(export.exportedAt()).isNotNull();
  }

  @Test
  @DisplayName("pessoa sem addresses/contacts/employments devolve listas vazias")
  void semDadosRelacionados() {
    Person person =
        Person.register(
            PersonType.PF,
            "Pessoa Vazia",
            null,
            CPF,
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
    PersonId id = person.id();

    given(persons.findById(id.value())).willReturn(Optional.of(person));
    given(modalities.findByPersonId(id.value())).willReturn(List.of());
    given(addresses.findByPersonId(id.value())).willReturn(List.of());
    given(contacts.findByPersonId(id.value())).willReturn(List.of());
    given(employees.findByPersonId(id.value())).willReturn(List.of());

    PersonalDataExport export = service.export(id, ACTOR);

    assertThat(export.addresses()).isEmpty();
    assertThat(export.contacts()).isEmpty();
    assertThat(export.employments()).isEmpty();
    assertThat(export.person().activeModalities()).isEmpty();
  }
}
