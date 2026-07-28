package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonAddressId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.PersonAddress;
import java.time.Instant;
import java.util.UUID;

/** Projeção plana de {@link PersonAddress} para camada web. */
public record PersonAddressSummary(
    PersonAddressId id,
    PersonId personId,
    UUID addressTypeId,
    String cep,
    String street,
    String number,
    String complement,
    String neighborhood,
    String city,
    String state,
    String country,
    String reference,
    boolean defaultAddress,
    Instant createdAt,
    Instant updatedAt) {

  public static PersonAddressSummary of(PersonAddress a) {
    return new PersonAddressSummary(
        new PersonAddressId(a.getId()),
        new PersonId(a.getPersonId()),
        a.getAddressTypeId(),
        a.getCep(),
        a.getStreet(),
        a.getNumber(),
        a.getComplement(),
        a.getNeighborhood(),
        a.getCity(),
        a.getState(),
        a.getCountry(),
        a.getReference(),
        a.isDefaultAddress(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }
}
