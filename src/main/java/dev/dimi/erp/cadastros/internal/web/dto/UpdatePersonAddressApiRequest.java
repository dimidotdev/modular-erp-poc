package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.PersonAddressId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.UpdatePersonAddressCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body do {@code PATCH /api/v1/cadastros/persons/{personId}/enderecos/{addressId}}.
 * Atualização parcial — campos {@code null} mantêm o valor atual.
 */
public record UpdatePersonAddressApiRequest(
    UUID addressTypeId,
    @Size(max = 10) String cep,
    @Size(max = 200) String street,
    @Size(max = 30) String number,
    @Size(max = 200) String complement,
    @Size(max = 200) String neighborhood,
    @Size(max = 200) String city,
    @Size(min = 2, max = 2) String state,
    @Size(min = 2, max = 2) String country,
    @Size(max = 500) String reference) {

  public UpdatePersonAddressCommand toCommand(
      PersonId personId, PersonAddressId addressId, UserId updatedBy) {
    return new UpdatePersonAddressCommand(
        personId,
        addressId,
        addressTypeId,
        cep,
        street,
        number,
        complement,
        neighborhood,
        city,
        state,
        country,
        reference,
        updatedBy);
  }
}
