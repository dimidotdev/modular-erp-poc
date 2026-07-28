package dev.dimi.erp.financeiro;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * MVF cancelada — só permitido em status {@code OPEN} sem nenhum pagamento aplicado (RN-FIN-010).
 * Consumido por auditoria; quando integração com gateway existir, listener pode cancelar boleto
 * remoto.
 */
public record FinancialRecordCancelledEvent(
    FinancialRecordId recordId,
    TenantId tenantId,
    String reason,
    UserId cancelledBy,
    Instant occurredAt) {}
