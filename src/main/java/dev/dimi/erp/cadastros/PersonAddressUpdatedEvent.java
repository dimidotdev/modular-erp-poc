package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Set;

/**
 * Endereço atualizado. {@code changedFields} permite à auditoria registrar o diff sem replicar
 * payload completo.
 */
public record PersonAddressUpdatedEvent(
    PersonId personId,
    PersonAddressId addressId,
    TenantId tenantId,
    Set<String> changedFields,
    UserId updatedBy,
    Instant occurredAt) {

  public PersonAddressUpdatedEvent {
    changedFields = changedFields == null ? Set.of() : Set.copyOf(changedFields);
  }
}
