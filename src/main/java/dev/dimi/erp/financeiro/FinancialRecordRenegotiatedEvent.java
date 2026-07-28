package dev.dimi.erp.financeiro;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * MVF renegociada — registro original muda para {@code RENEGOTIATED} e uma nova MVF substituta é
 * emitida (RN-FIN-011). Carrega ambos os IDs para auditoria/relatório de fluxo.
 */
public record FinancialRecordRenegotiatedEvent(
    FinancialRecordId originalRecordId,
    FinancialRecordId replacementRecordId,
    TenantId tenantId,
    BigDecimal originalRemainingValue,
    BigDecimal replacementTotalValue,
    String reason,
    UserId renegotiatedBy,
    Instant occurredAt) {}
