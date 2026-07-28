package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonContactId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.PersonContact;
import java.time.Instant;
import java.util.UUID;

/** Projeção plana de {@link PersonContact} para camada web. */
public record PersonContactSummary(
    PersonContactId id,
    PersonId personId,
    UUID contactTypeId,
    String value,
    String label,
    boolean primaryContact,
    Instant createdAt,
    Instant updatedAt) {

  public static PersonContactSummary of(PersonContact c) {
    return new PersonContactSummary(
        new PersonContactId(c.getId()),
        new PersonId(c.getPersonId()),
        c.getContactTypeId(),
        c.getValue(),
        c.getLabel(),
        c.isPrimaryContact(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
