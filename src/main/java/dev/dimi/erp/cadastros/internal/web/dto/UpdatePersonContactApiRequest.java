package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.PersonContactId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.UpdatePersonContactCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body do {@code PATCH /api/v1/cadastros/persons/{personId}/contatos/{contactId}}.
 * Atualização parcial — campos {@code null} mantêm o valor atual.
 */
public record UpdatePersonContactApiRequest(
    UUID contactTypeId, @Size(max = 200) String value, @Size(max = 80) String label) {

  public UpdatePersonContactCommand toCommand(
      PersonId personId, PersonContactId contactId, UserId updatedBy) {
    return new UpdatePersonContactCommand(
        personId, contactId, contactTypeId, value, label, updatedBy);
  }
}
