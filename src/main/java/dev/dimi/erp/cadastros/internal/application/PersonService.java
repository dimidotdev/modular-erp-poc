package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.ModalityActivatedEvent;
import dev.dimi.erp.cadastros.ModalityDeactivatedEvent;
import dev.dimi.erp.cadastros.PersonAnonymizedEvent;
import dev.dimi.erp.cadastros.PersonDeactivatedEvent;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonReactivatedEvent;
import dev.dimi.erp.cadastros.PersonRegisteredEvent;
import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.cadastros.PersonUpdatedEvent;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityAssignment;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityId;
import dev.dimi.erp.cadastros.internal.persistence.PersonAddressRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonContactRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonModalityRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.documents.Cnpj;
import dev.dimi.erp.shared.documents.Cpf;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre {@code Person} dentro do tenant atual.
 *
 * <p>Cobre o CRUD básico do slice 3.2: registrar, atualizar, soft-delete, reativar, listar/buscar,
 * mais ativação/desativação de modalidades. Anonimização LGPD e endereços/contatos ficam para
 * slices posteriores conforme {@code docs/domain/11-cadastros.md}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonService {

  private static final String CONSUMER_FINAL_CPF = "00000000000";
  private static final String CONSUMER_FINAL_CNPJ = "00000000000000";

  private final PersonRepository persons;
  private final PersonModalityRepository modalities;
  private final PersonAddressRepository addresses;
  private final PersonContactRepository contacts;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Registro
  // ---------------------------------------------------------------------------

  @Transactional
  public PersonSummary register(RegisterPersonCommand cmd) {
    String cpf = normalizeCpf(cmd);
    String cnpj = normalizeCnpj(cmd);
    rejectDuplicateDocument(cmd.type(), cpf, cnpj);

    Person saved =
        persons.save(
            Person.register(
                cmd.type(),
                cmd.legalName(),
                cmd.tradeName(),
                cpf,
                cnpj,
                cmd.rg(),
                cmd.ie(),
                cmd.im(),
                cmd.ieExempt(),
                cmd.birthDate(),
                cmd.foundationDate(),
                cmd.notes(),
                cmd.segmentId(),
                cmd.regionId()));

    List<PersonModalityAssignment> assigned = new ArrayList<>();
    for (Modality m : new TreeSet<>(cmd.modalities())) {
      assigned.add(modalities.save(PersonModalityAssignment.activate(saved.id(), m)));
    }

    Tenant tenant = TenantContext.currentOrThrow();
    TenantId tenantId = new TenantId(tenant.id());
    Instant now = Instant.now();

    events.publishEvent(
        new PersonRegisteredEvent(
            saved.id(),
            tenantId,
            saved.getType(),
            saved.getTradeName() != null ? saved.getTradeName() : saved.getLegalName(),
            documentOf(saved),
            new ArrayList<>(new TreeSet<>(cmd.modalities())),
            cmd.registeredBy(),
            now));
    for (PersonModalityAssignment pma : assigned) {
      events.publishEvent(
          new ModalityActivatedEvent(
              saved.id(), tenantId, pma.getId().getModality(), cmd.registeredBy(), now));
    }

    log.info(
        "pessoa registrada id={} type={} legalName='{}' modalities={}",
        saved.id().value(),
        saved.getType(),
        saved.getLegalName(),
        cmd.modalities());
    return PersonSummary.of(saved, assigned);
  }

  // ---------------------------------------------------------------------------
  // Atualização
  // ---------------------------------------------------------------------------

  @Transactional
  public PersonSummary update(UpdatePersonCommand cmd) {
    Person p = persons.findById(cmd.personId().value()).orElseThrow(() -> notFound(cmd.personId()));
    rejectIfAnonymized(p);
    if (!p.isActive()) {
      throw new InvalidPersonStateException(
          p.id(), "inactive", "Pessoa inativa não pode ser editada. Reative antes.");
    }

    Set<String> changed = diffUpdate(cmd, p);

    p.update(
        cmd.legalName(),
        cmd.tradeName(),
        cmd.rg(),
        cmd.ie(),
        cmd.im(),
        cmd.ieExempt(),
        cmd.birthDate(),
        cmd.foundationDate(),
        cmd.notes(),
        cmd.segmentId(),
        cmd.regionId());

    if (!changed.isEmpty()) {
      Tenant tenant = TenantContext.currentOrThrow();
      events.publishEvent(
          new PersonUpdatedEvent(
              p.id(), new TenantId(tenant.id()), changed, cmd.updatedBy(), Instant.now()));
      log.info("pessoa atualizada id={} fields={}", p.id().value(), changed);
    }
    return PersonSummary.of(p, modalities.findByPersonId(p.id().value()));
  }

  private static Set<String> diffUpdate(UpdatePersonCommand cmd, Person p) {
    Set<String> changed = new HashSet<>();
    addIfChanged(changed, "legalName", cmd.legalName(), p.getLegalName());
    addIfChanged(changed, "tradeName", cmd.tradeName(), p.getTradeName());
    addIfChanged(changed, "rg", cmd.rg(), p.getRg());
    addIfChanged(changed, "ie", cmd.ie(), p.getIe());
    addIfChanged(changed, "im", cmd.im(), p.getIm());
    addIfChanged(changed, "notes", cmd.notes(), p.getNotes());
    addIfChanged(changed, "birthDate", cmd.birthDate(), p.getBirthDate());
    addIfChanged(changed, "foundationDate", cmd.foundationDate(), p.getFoundationDate());
    addIfChanged(changed, "segmentId", cmd.segmentId(), p.getSegmentId());
    addIfChanged(changed, "regionId", cmd.regionId(), p.getRegionId());
    if (cmd.ieExempt() != null && cmd.ieExempt() != p.isIeExempt()) {
      changed.add("ieExempt");
    }
    return changed;
  }

  private static <T> void addIfChanged(Set<String> sink, String field, T incoming, T current) {
    if (incoming != null && !incoming.equals(current)) {
      sink.add(field);
    }
  }

  // ---------------------------------------------------------------------------
  // Soft-delete / reativação
  // ---------------------------------------------------------------------------

  @Transactional
  public PersonSummary deactivate(PersonId personId, String reason, UserId actor) {
    Person p = persons.findById(personId.value()).orElseThrow(() -> notFound(personId));
    if (!p.isActive()) {
      throw new InvalidPersonStateException(p.id(), "already-inactive", "Pessoa já está inativa.");
    }
    p.deactivate();

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonDeactivatedEvent(
            p.id(), new TenantId(tenant.id()), reason, actor, Instant.now()));
    log.info("pessoa desativada id={} reason={}", p.id().value(), reason);
    return PersonSummary.of(p, modalities.findByPersonId(p.id().value()));
  }

  @Transactional
  public PersonSummary reactivate(PersonId personId, UserId actor) {
    Person p = persons.findById(personId.value()).orElseThrow(() -> notFound(personId));
    rejectIfAnonymized(p);
    if (p.isActive()) {
      throw new InvalidPersonStateException(p.id(), "already-active", "Pessoa já está ativa.");
    }
    p.reactivate();

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonReactivatedEvent(p.id(), new TenantId(tenant.id()), actor, Instant.now()));
    log.info("pessoa reativada id={}", p.id().value());
    return PersonSummary.of(p, modalities.findByPersonId(p.id().value()));
  }

  // ---------------------------------------------------------------------------
  // Anonimização LGPD (RN-CAD-011/012)
  // ---------------------------------------------------------------------------

  /**
   * Anonimiza a pessoa (RN-CAD-011/012). Operação irreversível: zera PII na entidade, apaga
   * endereços e contatos, desativa modalidades ativas e publica {@link PersonAnonymizedEvent}.
   *
   * <p>O caller (controller) é responsável por exigir step-up via {@code StepUpPolicy.requireFresh}
   * antes de invocar este método.
   */
  @Transactional
  public PersonSummary anonymize(PersonId personId, String reason, UserId actor) {
    Person p = persons.findById(personId.value()).orElseThrow(() -> notFound(personId));
    if (p.isAnonymized()) {
      throw new InvalidPersonStateException(
          p.id(), "already-anonymized", "Pessoa já está anonimizada.");
    }

    p.anonymize();
    addresses.deleteByPersonId(personId.value());
    contacts.deleteByPersonId(personId.value());

    List<PersonModalityAssignment> mods = modalities.findByPersonId(personId.value());
    for (PersonModalityAssignment pma : mods) {
      if (pma.isActive()) {
        pma.deactivate();
      }
    }

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonAnonymizedEvent(p.id(), new TenantId(tenant.id()), reason, actor, Instant.now()));
    log.info("pessoa anonimizada id={} reason={}", p.id().value(), reason);
    return PersonSummary.of(p, mods);
  }

  // ---------------------------------------------------------------------------
  // Modalidades
  // ---------------------------------------------------------------------------

  @Transactional
  public PersonSummary activateModality(PersonId personId, Modality modality, UserId actor) {
    Person p = persons.findById(personId.value()).orElseThrow(() -> notFound(personId));
    rejectIfAnonymized(p);
    if (!p.isActive()) {
      throw new InvalidPersonStateException(
          p.id(), "inactive", "Pessoa inativa não pode receber modalidade.");
    }

    PersonModalityId id = new PersonModalityId(personId.value(), modality);
    Optional<PersonModalityAssignment> existing = modalities.findById(id);
    if (existing.isPresent()) {
      PersonModalityAssignment current = existing.get();
      if (current.isActive()) {
        throw new ModalityAlreadyActiveException(personId, modality);
      }
      current.reactivate();
    } else {
      modalities.save(PersonModalityAssignment.activate(personId, modality));
    }

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new ModalityActivatedEvent(
            personId, new TenantId(tenant.id()), modality, actor, Instant.now()));
    log.info("modalidade ativada person={} modality={}", personId.value(), modality);
    return PersonSummary.of(p, modalities.findByPersonId(personId.value()));
  }

  @Transactional
  public PersonSummary deactivateModality(
      PersonId personId, Modality modality, String reason, UserId actor) {
    Person p = persons.findById(personId.value()).orElseThrow(() -> notFound(personId));

    PersonModalityId id = new PersonModalityId(personId.value(), modality);
    PersonModalityAssignment pma =
        modalities
            .findById(id)
            .orElseThrow(() -> new ModalityNotActiveException(personId, modality));
    if (!pma.isActive()) {
      throw new ModalityNotActiveException(personId, modality);
    }
    pma.deactivate();

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new ModalityDeactivatedEvent(
            personId, new TenantId(tenant.id()), modality, reason, actor, Instant.now()));
    log.info(
        "modalidade desativada person={} modality={} reason={}",
        personId.value(),
        modality,
        reason);
    return PersonSummary.of(p, modalities.findByPersonId(personId.value()));
  }

  // ---------------------------------------------------------------------------
  // Queries
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public PersonSummary findById(PersonId personId) {
    Person p = persons.findById(personId.value()).orElseThrow(() -> notFound(personId));
    return PersonSummary.of(p, modalities.findByPersonId(personId.value()));
  }

  @Transactional(readOnly = true)
  public Page<PersonSummary> list(Pageable pageable) {
    Page<Person> page = persons.findAll(pageable);
    if (page.isEmpty()) {
      return page.map(p -> PersonSummary.of(p, List.of()));
    }
    return page.map(p -> PersonSummary.of(p, modalities.findByPersonId(p.id().value())));
  }

  @Transactional(readOnly = true)
  public Page<PersonSummary> searchByLegalName(String term, Pageable pageable) {
    Page<Person> page = persons.searchByLegalName(term, pageable);
    return page.map(p -> PersonSummary.of(p, modalities.findByPersonId(p.id().value())));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static String normalizeCpf(RegisterPersonCommand cmd) {
    if (cmd.type() != PersonType.PF) {
      if (cmd.cpf() != null) {
        throw new InvalidPersonStateException(null, "cpf-on-pj", "Pessoa PJ não pode ter CPF.");
      }
      return null;
    }
    if (cmd.cpf() == null || cmd.cpf().isBlank()) {
      throw new InvalidPersonStateException(null, "missing-cpf", "Pessoa PF exige CPF.");
    }
    return Cpf.of(cmd.cpf()).value();
  }

  private static String normalizeCnpj(RegisterPersonCommand cmd) {
    if (cmd.type() != PersonType.PJ) {
      if (cmd.cnpj() != null) {
        throw new InvalidPersonStateException(null, "cnpj-on-pf", "Pessoa PF não pode ter CNPJ.");
      }
      return null;
    }
    if (cmd.cnpj() == null || cmd.cnpj().isBlank()) {
      throw new InvalidPersonStateException(null, "missing-cnpj", "Pessoa PJ exige CNPJ.");
    }
    return Cnpj.of(cmd.cnpj()).value();
  }

  private void rejectDuplicateDocument(PersonType type, String cpf, String cnpj) {
    if (type == PersonType.PF
        && cpf != null
        && !CONSUMER_FINAL_CPF.equals(cpf)
        && persons.existsByCpf(cpf)) {
      throw new PersonAlreadyExistsException(type, cpf);
    }
    if (type == PersonType.PJ
        && cnpj != null
        && !CONSUMER_FINAL_CNPJ.equals(cnpj)
        && persons.existsByCnpj(cnpj)) {
      throw new PersonAlreadyExistsException(type, cnpj);
    }
  }

  private void rejectIfAnonymized(Person p) {
    if (p.isAnonymized()) {
      throw new InvalidPersonStateException(
          p.id(), "anonymized-immutable", "Pessoa anonimizada não pode ser modificada.");
    }
  }

  private static String documentOf(Person p) {
    String doc = p.getType() == PersonType.PF ? p.getCpf() : p.getCnpj();
    return doc != null ? doc : "";
  }

  private static PersonNotFoundException notFound(PersonId id) {
    return new PersonNotFoundException(id);
  }
}
