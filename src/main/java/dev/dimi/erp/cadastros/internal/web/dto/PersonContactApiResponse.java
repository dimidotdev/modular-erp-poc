package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.PersonContactId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.PersonContactSummary;
import java.time.Instant;
import java.util.UUID;

/** Response do {@code POST/GET/PATCH /api/v1/cadastros/persons/{id}/contatos[/{contactId}]}. */
public record PersonContactApiResponse(
    PersonContactId id,
    PersonId personId,
    UUID contactTypeId,
    String value,
    String label,
    boolean primaryContact,
    Instant createdAt,
    Instant updatedAt) {

  public static PersonContactApiResponse from(PersonContactSummary s) {
    return new PersonContactApiResponse(
        s.id(),
        s.personId(),
        s.contactTypeId(),
        s.value(),
        s.label(),
        s.primaryContact(),
        s.createdAt(),
        s.updatedAt());
  }
}
