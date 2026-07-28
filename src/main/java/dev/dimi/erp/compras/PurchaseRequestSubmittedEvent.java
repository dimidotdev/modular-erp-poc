package dev.dimi.erp.compras;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * SC submetida para aprovação (RN-COM-002). Consumido por {@code auditoria} e (futuro) sistema de
 * notificação para alertar aprovador.
 */
public record PurchaseRequestSubmittedEvent(
    PurchaseRequestId requestId,
    TenantId tenantId,
    BranchId branchId,
    UserId requestedBy,
    Instant occurredAt) {}
