package dev.dimi.erp.financeiro;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * Pagamento estornado (RN-FIN-013). UNIQUE(original_payment_id) no schema garante 1 estorno por
 * pagamento — re-tentativa retorna o estorno existente sem efeitos colaterais.
 */
public record PaymentReversedEvent(
    UUID reversalId,
    PaymentId originalPaymentId,
    PaymentId reversalPaymentId,
    TenantId tenantId,
    String reason,
    UserId reversedBy,
    Instant occurredAt) {}
