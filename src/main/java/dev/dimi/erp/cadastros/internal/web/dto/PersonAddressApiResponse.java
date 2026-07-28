package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.PersonAddressId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.PersonAddressSummary;
import java.time.Instant;
import java.util.UUID;

/** Response do {@code POST/GET/PATCH /api/v1/cadastros/persons/{id}/enderecos[/{addressId}]}. */
public record PersonAddressApiResponse(
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

  public static PersonAddressApiResponse from(PersonAddressSummary s) {
    return new PersonAddressApiResponse(
        s.id(),
        s.personId(),
        s.addressTypeId(),
        s.cep(),
        s.street(),
        s.number(),
        s.complement(),
        s.neighborhood(),
        s.city(),
        s.state(),
        s.country(),
        s.reference(),
        s.defaultAddress(),
        s.createdAt(),
        s.updatedAt());
  }
}
