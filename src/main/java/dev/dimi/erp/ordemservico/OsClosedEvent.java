package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * OS encerrada — vai pro {@code CLOSED}, read-only daqui pra frente. Consumidores: {@code
 * auditoria}.
 */
public record OsClosedEvent(
    ServiceOrderId osId, TenantId tenantId, UserId closedBy, Instant occurredAt) {}
