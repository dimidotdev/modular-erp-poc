package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/** Contato removido (hard delete). */
public record PersonContactDeletedEvent(
    PersonId personId,
    PersonContactId contactId,
    TenantId tenantId,
    UserId deletedBy,
    Instant occurredAt) {}
