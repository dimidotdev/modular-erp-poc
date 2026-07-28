package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonContactId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import java.util.UUID;

/** Atualização parcial de contato. Campos {@code null} preservam o valor atual. */
public record UpdatePersonContactCommand(
    PersonId personId,
    PersonContactId contactId,
    UUID contactTypeId,
    String value,
    String label,
    UserId updatedBy) {

  public UpdatePersonContactCommand {
    if (personId == null) throw new IllegalArgumentException("personId obrigatório");
    if (contactId == null) throw new IllegalArgumentException("contactId obrigatório");
  }
}
