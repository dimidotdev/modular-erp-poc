package dev.dimi.erp.financeiro.internal.application;

/**
 * Estorna pagamento prévio gerando pagamento espelho (mesma conta bancária, mesmo método) com
 * valores invertidos. UNIQUE(original_payment_id) no schema impede duplo estorno — service retorna
 * o estorno existente se já houver.
 */
public record ReverseFinancialPaymentCommand(
    dev.dimi.erp.financeiro.PaymentId originalPaymentId,
    String reason,
    dev.dimi.erp.identidade.UserId actor) {}
