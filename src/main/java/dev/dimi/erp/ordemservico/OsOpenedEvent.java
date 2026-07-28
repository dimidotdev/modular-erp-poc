package dev.dimi.erp.ordemservico;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * OS aberta. Consumidores conhecidos: {@code auditoria}.
 *
 * <p>Evento gordinho para consumers não precisarem reabrir via QueryPort.
 */
public record OsOpenedEvent(
    ServiceOrderId osId,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    long number,
    OsPriority priority,
    UserId createdBy,
    UUID assignedTo,
    Instant occurredAt) {}
