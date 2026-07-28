package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Set;

/** Contato atualizado; {@code changedFields} dá o diff para auditoria. */
public record PersonContactUpdatedEvent(
    PersonId personId,
    PersonContactId contactId,
    TenantId tenantId,
    Set<String> changedFields,
    UserId updatedBy,
    Instant occurredAt) {

  public PersonContactUpdatedEvent {
    changedFields = changedFields == null ? Set.of() : Set.copyOf(changedFields);
  }
}
