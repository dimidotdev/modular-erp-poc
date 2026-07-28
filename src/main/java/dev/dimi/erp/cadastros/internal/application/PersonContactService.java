package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonContactCreatedEvent;
import dev.dimi.erp.cadastros.PersonContactDeletedEvent;
import dev.dimi.erp.cadastros.PersonContactId;
import dev.dimi.erp.cadastros.PersonContactUpdatedEvent;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.domain.PersonContact;
import dev.dimi.erp.cadastros.internal.persistence.PersonContactRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre contatos de pessoas no tenant atual.
 *
 * <p>RN-CAD-004 análoga ao endereço: no máximo um {@code is_primary=true} por pessoa, garantido
 * pelo service ao reatribuir antes de salvar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonContactService {

  private final PersonRepository persons;
  private final PersonContactRepository contacts;
  private final ApplicationEventPublisher events;

  @Transactional
  public PersonContactSummary create(CreatePersonContactCommand cmd) {
    Person p = loadActivePerson(cmd.personId());

    if (cmd.primaryContact()) {
      unsetCurrentPrimary(p.id().value());
    }

    PersonContact saved =
        contacts.save(
            PersonContact.create(
                p.id().value(),
                cmd.contactTypeId(),
                cmd.value(),
                cmd.label(),
                cmd.primaryContact()));

    publishCreated(saved, cmd.createdBy());
    log.info(
        "contato criado person={} contact={} primary={}",
        p.id().value(),
        saved.getId(),
        saved.isPrimaryContact());
    return PersonContactSummary.of(saved);
  }

  @Transactional
  public PersonContactSummary update(UpdatePersonContactCommand cmd) {
    Person p = loadActivePerson(cmd.personId());
    PersonContact c = loadContact(p, cmd.contactId());

    Set<String> changed = diffUpdate(cmd, c);
    c.update(cmd.contactTypeId(), cmd.value(), cmd.label());

    if (!changed.isEmpty()) {
      publishUpdated(c, changed, cmd.updatedBy());
      log.info(
          "contato atualizado person={} contact={} fields={}", p.id().value(), c.getId(), changed);
    }
    return PersonContactSummary.of(c);
  }

  @Transactional
  public PersonContactSummary setAsPrimary(
      PersonId personId, PersonContactId contactId, UserId by) {
    Person p = loadActivePerson(personId);
    PersonContact c = loadContact(p, contactId);
    if (!c.isPrimaryContact()) {
      unsetCurrentPrimary(personId.value());
      c.markAsPrimary();
      publishUpdated(c, Set.of("primaryContact"), by);
      log.info(
          "contato marcado como primário person={} contact={}",
          personId.value(),
          contactId.value());
    }
    return PersonContactSummary.of(c);
  }

  @Transactional
  public void delete(PersonId personId, PersonContactId contactId, UserId by) {
    Person p = loadActivePerson(personId);
    PersonContact c = loadContact(p, contactId);
    contacts.delete(c);

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonContactDeletedEvent(
            personId, contactId, new TenantId(tenant.id()), by, Instant.now()));
    log.info("contato removido person={} contact={}", personId.value(), contactId.value());
  }

  @Transactional(readOnly = true)
  public List<PersonContactSummary> list(PersonId personId) {
    loadActivePerson(personId);
    return contacts.findByPersonId(personId.value()).stream()
        .map(PersonContactSummary::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public PersonContactSummary findById(PersonId personId, PersonContactId contactId) {
    Person p = loadActivePerson(personId);
    return PersonContactSummary.of(loadContact(p, contactId));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Person loadActivePerson(PersonId personId) {
    Person p =
        persons.findById(personId.value()).orElseThrow(() -> new PersonNotFoundException(personId));
    if (p.isAnonymized()) {
      throw new InvalidPersonStateException(
          p.id(), "anonymized-immutable", "Pessoa anonimizada não pode ter contatos alterados.");
    }
    if (!p.isActive()) {
      throw new InvalidPersonStateException(
          p.id(), "inactive", "Pessoa inativa não aceita alteração de contatos.");
    }
    return p;
  }

  private PersonContact loadContact(Person p, PersonContactId contactId) {
    PersonContact c =
        contacts
            .findById(contactId.value())
            .orElseThrow(() -> new PersonContactNotFoundException(p.id(), contactId));
    if (!c.getPersonId().equals(p.id().value())) {
      throw new PersonContactNotFoundException(p.id(), contactId);
    }
    return c;
  }

  private void unsetCurrentPrimary(java.util.UUID personUuid) {
    Optional<PersonContact> current = contacts.findByPersonIdAndPrimaryContactTrue(personUuid);
    current.ifPresent(PersonContact::unmarkAsPrimary);
  }

  private static Set<String> diffUpdate(UpdatePersonContactCommand cmd, PersonContact c) {
    Set<String> changed = new HashSet<>();
    addIfChanged(changed, "contactTypeId", cmd.contactTypeId(), c.getContactTypeId());
    addIfChanged(changed, "value", cmd.value(), c.getValue());
    addIfChanged(changed, "label", cmd.label(), c.getLabel());
    return changed;
  }

  private static <T> void addIfChanged(Set<String> sink, String field, T incoming, T current) {
    if (incoming != null && !incoming.equals(current)) {
      sink.add(field);
    }
  }

  private void publishCreated(PersonContact c, UserId by) {
    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonContactCreatedEvent(
            new PersonId(c.getPersonId()),
            new PersonContactId(c.getId()),
            new TenantId(tenant.id()),
            c.isPrimaryContact(),
            by,
            Instant.now()));
  }

  private void publishUpdated(PersonContact c, Set<String> changed, UserId by) {
    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonContactUpdatedEvent(
            new PersonId(c.getPersonId()),
            new PersonContactId(c.getId()),
            new TenantId(tenant.id()),
            changed,
            by,
            Instant.now()));
  }
}
