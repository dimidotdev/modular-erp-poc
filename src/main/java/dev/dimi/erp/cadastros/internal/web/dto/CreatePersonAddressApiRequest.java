package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.CreatePersonAddressCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request body do {@code POST /api/v1/cadastros/persons/{id}/enderecos}. */
public record CreatePersonAddressApiRequest(
    UUID addressTypeId,
    @NotBlank @Size(max = 10) String cep,
    @NotBlank @Size(max = 200) String street,
    @Size(max = 30) String number,
    @Size(max = 200) String complement,
    @Size(max = 200) String neighborhood,
    @NotBlank @Size(max = 200) String city,
    @NotBlank @Size(min = 2, max = 2) String state,
    @Size(min = 2, max = 2) String country,
    @Size(max = 500) String reference,
    Boolean defaultAddress) {

  public CreatePersonAddressCommand toCommand(PersonId personId, UserId createdBy) {
    return new CreatePersonAddressCommand(
        personId,
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
        defaultAddress != null && defaultAddress,
        createdBy);
  }
}
