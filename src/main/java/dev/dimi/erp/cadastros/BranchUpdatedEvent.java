package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Set;

/**
 * Filial teve campos editados. {@code changedFields} contém os nomes dos campos alterados (ex:
 * {@code name}, {@code addressId}, {@code main}). Emitido apenas quando ao menos um campo mudou de
 * fato.
 */
public record BranchUpdatedEvent(
    BranchId branchId,
    TenantId tenantId,
    Set<String> changedFields,
    UserId updatedBy,
    Instant occurredAt) {

  public BranchUpdatedEvent {
    changedFields = changedFields == null ? Set.of() : Set.copyOf(changedFields);
  }
}
