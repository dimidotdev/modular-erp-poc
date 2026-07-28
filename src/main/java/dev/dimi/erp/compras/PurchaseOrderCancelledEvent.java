package dev.dimi.erp.compras;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * PC cancelado. RN-COM-010: pode ser cancelado em qualquer estado antes de RECEIVED. Em
 * PARTIALLY_RECEIVED, apenas itens não recebidos cancelados (slice 6.3 trata).
 */
public record PurchaseOrderCancelledEvent(
    PurchaseOrderId orderId,
    TenantId tenantId,
    String reason,
    UserId cancelledBy,
    Instant occurredAt) {}
