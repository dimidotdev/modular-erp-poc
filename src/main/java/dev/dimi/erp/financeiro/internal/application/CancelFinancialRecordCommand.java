package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.identidade.UserId;

/**
 * Cancela uma MVF. v1: só permitido em status {@code OPEN} sem nenhum pagamento aplicado
 * (RN-FIN-010). Pagamentos existentes exigem estorno via {@code /payments/{id}/reverse} antes.
 */
public record CancelFinancialRecordCommand(
    FinancialRecordId recordId, String reason, UserId actor) {}
