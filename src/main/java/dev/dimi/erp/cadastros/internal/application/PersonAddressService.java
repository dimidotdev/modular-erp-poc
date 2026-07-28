package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonAddressCreatedEvent;
import dev.dimi.erp.cadastros.PersonAddressDeletedEvent;
import dev.dimi.erp.cadastros.PersonAddressId;
import dev.dimi.erp.cadastros.PersonAddressUpdatedEvent;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.domain.PersonAddress;
import dev.dimi.erp.cadastros.internal.persistence.PersonAddressRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.address.Cep;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre endereços de pessoas no tenant atual.
 *
 * <p>RN-CAD-004: no máximo um {@code is_default=true} por pessoa — garantido por índice parcial em
 * banco e pelo service ao reatribuir o padrão antes de salvar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonAddressService {

  private final PersonRepository persons;
  private final PersonAddressRepository addresses;
  private final ApplicationEventPublisher events;

  @Transactional
  public PersonAddressSummary create(CreatePersonAddressCommand cmd) {
    Person p = loadActivePerson(cmd.personId());

    if (cmd.defaultAddress()) {
      addresses.clearDefaultForPerson(p.id().value());
    }

    PersonAddress saved =
        addresses.save(
            PersonAddress.create(
                p.id().value(),
                cmd.addressTypeId(),
                Cep.of(cmd.cep()).value(),
                cmd.street(),
                cmd.number(),
                cmd.complement(),
                cmd.neighborhood(),
                cmd.city(),
                cmd.state(),
                cmd.country(),
                cmd.reference(),
                cmd.defaultAddress()));

    publishCreated(saved, cmd.createdBy());
    log.info(
        "endereço criado person={} address={} default={}",
        p.id().value(),
        saved.getId(),
        saved.isDefaultAddress());
    return PersonAddressSummary.of(saved);
  }

  @Transactional
  public PersonAddressSummary update(UpdatePersonAddressCommand cmd) {
    Person p = loadActivePerson(cmd.personId());
    PersonAddress a = loadAddress(p, cmd.addressId());

    Set<String> changed = diffUpdate(cmd, a);
    a.update(
        cmd.addressTypeId(),
        cmd.cep() == null ? null : Cep.of(cmd.cep()).value(),
        cmd.street(),
        cmd.number(),
        cmd.complement(),
        cmd.neighborhood(),
        cmd.city(),
        cmd.state(),
        cmd.country(),
        cmd.reference());

    if (!changed.isEmpty()) {
      publishUpdated(a, changed, cmd.updatedBy());
      log.info(
          "endereço atualizado person={} address={} fields={}", p.id().value(), a.getId(), changed);
    }
    return PersonAddressSummary.of(a);
  }

  @Transactional
  public PersonAddressSummary setAsDefault(
      PersonId personId, PersonAddressId addressId, UserId by) {
    Person p = loadActivePerson(personId);
    PersonAddress a = loadAddress(p, addressId);
    if (!a.isDefaultAddress()) {
      addresses.clearDefaultForPerson(personId.value());
      a.markAsDefault();
      publishUpdated(a, Set.of("defaultAddress"), by);
      log.info(
          "endereço marcado como padrão person={} address={}", personId.value(), addressId.value());
    }
    return PersonAddressSummary.of(a);
  }

  @Transactional
  public void delete(PersonId personId, PersonAddressId addressId, UserId by) {
    Person p = loadActivePerson(personId);
    PersonAddress a = loadAddress(p, addressId);
    addresses.delete(a);

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonAddressDeletedEvent(
            personId, addressId, new TenantId(tenant.id()), by, Instant.now()));
    log.info("endereço removido person={} address={}", personId.value(), addressId.value());
  }

  @Transactional(readOnly = true)
  public List<PersonAddressSummary> list(PersonId personId) {
    loadActivePerson(personId);
    return addresses.findByPersonId(personId.value()).stream()
        .map(PersonAddressSummary::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public PersonAddressSummary findById(PersonId personId, PersonAddressId addressId) {
    Person p = loadActivePerson(personId);
    return PersonAddressSummary.of(loadAddress(p, addressId));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Person loadActivePerson(PersonId personId) {
    Person p =
        persons.findById(personId.value()).orElseThrow(() -> new PersonNotFoundException(personId));
    if (p.isAnonymized()) {
      throw new InvalidPersonStateException(
          p.id(), "anonymized-immutable", "Pessoa anonimizada não pode ter endereços alterados.");
    }
    if (!p.isActive()) {
      throw new InvalidPersonStateException(
          p.id(), "inactive", "Pessoa inativa não aceita alteração de endereços.");
    }
    return p;
  }

  private PersonAddress loadAddress(Person p, PersonAddressId addressId) {
    PersonAddress a =
        addresses
            .findById(addressId.value())
            .orElseThrow(() -> new PersonAddressNotFoundException(p.id(), addressId));
    if (!a.getPersonId().equals(p.id().value())) {
      throw new PersonAddressNotFoundException(p.id(), addressId);
    }
    return a;
  }

  private static Set<String> diffUpdate(UpdatePersonAddressCommand cmd, PersonAddress a) {
    Set<String> changed = new HashSet<>();
    addIfChanged(changed, "addressTypeId", cmd.addressTypeId(), a.getAddressTypeId());
    addIfChanged(changed, "cep", cmd.cep() == null ? null : Cep.of(cmd.cep()).value(), a.getCep());
    addIfChanged(changed, "street", cmd.street(), a.getStreet());
    addIfChanged(changed, "number", cmd.number(), a.getNumber());
    addIfChanged(changed, "complement", cmd.complement(), a.getComplement());
    addIfChanged(changed, "neighborhood", cmd.neighborhood(), a.getNeighborhood());
    addIfChanged(changed, "city", cmd.city(), a.getCity());
    addIfChanged(changed, "state", cmd.state(), a.getState());
    addIfChanged(changed, "country", cmd.country(), a.getCountry());
    addIfChanged(changed, "reference", cmd.reference(), a.getReference());
    return changed;
  }

  private static <T> void addIfChanged(Set<String> sink, String field, T incoming, T current) {
    if (incoming != null && !incoming.equals(current)) {
      sink.add(field);
    }
  }

  private void publishCreated(PersonAddress a, UserId by) {
    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonAddressCreatedEvent(
            new PersonId(a.getPersonId()),
            new PersonAddressId(a.getId()),
            new TenantId(tenant.id()),
            a.isDefaultAddress(),
            by,
            Instant.now()));
  }

  private void publishUpdated(PersonAddress a, Set<String> changed, UserId by) {
    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new PersonAddressUpdatedEvent(
            new PersonId(a.getPersonId()),
            new PersonAddressId(a.getId()),
            new TenantId(tenant.id()),
            changed,
            by,
            Instant.now()));
  }
}
