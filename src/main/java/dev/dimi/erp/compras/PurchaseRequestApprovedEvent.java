package dev.dimi.erp.compras;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/** SC aprovada — disponível para gerar cotação ou PC direto (RN-COM-004). */
public record PurchaseRequestApprovedEvent(
    PurchaseRequestId requestId, TenantId tenantId, UserId approvedBy, Instant occurredAt) {}
