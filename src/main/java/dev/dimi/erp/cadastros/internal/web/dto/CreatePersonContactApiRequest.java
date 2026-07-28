package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.CreatePersonContactCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request body do {@code POST /api/v1/cadastros/persons/{id}/contatos}. */
public record CreatePersonContactApiRequest(
    UUID contactTypeId,
    @NotBlank @Size(max = 200) String value,
    @Size(max = 80) String label,
    Boolean primaryContact) {

  public CreatePersonContactCommand toCommand(PersonId personId, UserId createdBy) {
    return new CreatePersonContactCommand(
        personId, contactTypeId, value, label, primaryContact != null && primaryContact, createdBy);
  }
}
