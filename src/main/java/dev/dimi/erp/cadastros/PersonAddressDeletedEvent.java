package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/** Endereço removido (hard delete). */
public record PersonAddressDeletedEvent(
    PersonId personId,
    PersonAddressId addressId,
    TenantId tenantId,
    UserId deletedBy,
    Instant occurredAt) {}
