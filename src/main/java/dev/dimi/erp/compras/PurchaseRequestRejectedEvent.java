package dev.dimi.erp.compras;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/** SC rejeitada — auditoria registra com motivo. */
public record PurchaseRequestRejectedEvent(
    PurchaseRequestId requestId,
    TenantId tenantId,
    String reason,
    UserId rejectedBy,
    Instant occurredAt) {}
