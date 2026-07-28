package dev.dimi.erp.financeiro;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Batch de pagamento processado (RN-FIN-007/008/009) — 1 POST agrupa N pagamentos com mesma forma e
 * conta bancária, cada um aplicado em 1..N parcelas. Consumido por auditoria; quando integração
 * bancária existir, listener pode reconciliar com extrato.
 */
public record PaymentProcessedEvent(
    BatchId batchId,
    TenantId tenantId,
    BankAccountId bankAccountId,
    PaymentMethod method,
    List<PaymentId> paymentIds,
    BigDecimal totalApplied,
    UserId processedBy,
    Instant occurredAt) {}
