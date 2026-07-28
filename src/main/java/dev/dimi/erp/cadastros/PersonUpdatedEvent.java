package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Set;

/**
 * Pessoa atualizada via {@code PATCH /api/v1/cadastros/pessoas/{id}}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code PERSON_UPDATED}). O
 * conjunto {@code changedFields} permite registrar o diff sem replicar payload completo.
 */
public record PersonUpdatedEvent(
    PersonId personId,
    TenantId tenantId,
    Set<String> changedFields,
    UserId updatedBy,
    Instant occurredAt) {

  public PersonUpdatedEvent {
    changedFields = changedFields == null ? Set.of() : Set.copyOf(changedFields);
  }
}
