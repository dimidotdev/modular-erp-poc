package dev.dimi.erp.financeiro.internal.application;

/**
 * Snapshot of {@link dev.dimi.erp.financeiro.internal.domain.FinancialPayment} with its
 * applications.
 */
public record FinancialPaymentSummary(
    dev.dimi.erp.financeiro.PaymentId id,
    long version,
    dev.dimi.erp.financeiro.BatchId batchId,
    java.time.Instant occurredAt,
    dev.dimi.erp.financeiro.BankAccountId bankAccountId,
    dev.dimi.erp.financeiro.PaymentMethod method,
    dev.dimi.erp.identidade.UserId userId,
    String notes,
    String idempotencyKey,
    java.time.Instant createdAt,
    java.util.List<FinancialPaymentApplicationSummary> applications) {

  public static FinancialPaymentSummary of(
      dev.dimi.erp.financeiro.internal.domain.FinancialPayment p,
      java.util.List<FinancialPaymentApplicationSummary> applications) {
    return new FinancialPaymentSummary(
        p.id(),
        p.getVersion(),
        p.batchIdTyped(),
        p.getOccurredAt(),
        p.bankAccountIdTyped(),
        p.getMethod(),
        p.userIdTyped(),
        p.getNotes(),
        p.getIdempotencyKey(),
        p.getCreatedAt(),
        applications);
  }
}
