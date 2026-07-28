package dev.dimi.erp.financeiro;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * Listener financeiro recebeu um evento upstream (SaleClosed/PurchaseReceived/etc) mas não
 * conseguiu criar a MVF — geralmente porque {@code tenant_setting} não está configurada com
 * conta-default. Operador resolve cadastrando settings via {@code PUT /api/v1/financeiro/settings}
 * e depois pode reprocessar manualmente (operação não automatizada em v1).
 *
 * <p>Consumido por auditoria; opcionalmente por sistema de alerta/notificação.
 */
public record FinancialListenerSkippedEvent(
    TenantId tenantId,
    String upstreamEvent,
    UUID upstreamId,
    String reasonCode,
    String reasonDetail,
    Instant occurredAt) {}
