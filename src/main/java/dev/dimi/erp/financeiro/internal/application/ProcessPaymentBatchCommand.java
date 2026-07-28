package dev.dimi.erp.financeiro.internal.application;

/**
 * Processa N aplicações de pagamento em parcelas. v1: todas as aplicações compartilham o mesmo
 * {@code method} + {@code bankAccountId}; quando precisar mistura de métodos no mesmo POST, criar
 * variante. {@code idempotencyKey} obrigatório (header Idempotency-Key).
 */
public record ProcessPaymentBatchCommand(
    dev.dimi.erp.financeiro.BankAccountId bankAccountId,
    dev.dimi.erp.financeiro.PaymentMethod method,
    java.time.Instant occurredAt,
    java.util.List<Application> applications,
    String notes,
    String idempotencyKey,
    dev.dimi.erp.identidade.UserId actor) {

  public record Application(
      dev.dimi.erp.financeiro.InstallmentId installmentId,
      java.math.BigDecimal value,
      java.math.BigDecimal fineApplied,
      java.math.BigDecimal interestApplied,
      java.math.BigDecimal discountApplied) {}
}
