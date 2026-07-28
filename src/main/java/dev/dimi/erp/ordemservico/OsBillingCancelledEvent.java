package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Faturamento da OS cancelado — primeira etapa da saga inversa. Dispara o estorno em cascata:
 * cancelamento de NFe/NFSe via {@code FiscalProvider}, estorno de MVF no {@code financeiro} e
 * devolução do estoque consumido ao saldo. Consumidores: {@code auditoria}; eventualmente {@code
 * fiscal} (para iniciar o cancelamento dos documentos) e {@code financeiro} (para estornar MVF).
 */
public record OsBillingCancelledEvent(
    BillingId billingId,
    ServiceOrderId osId,
    TenantId tenantId,
    String reason,
    UserId cancelledBy,
    Instant occurredAt) {}
