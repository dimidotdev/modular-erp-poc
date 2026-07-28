package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.BatchId;
import dev.dimi.erp.financeiro.PaymentId;
import dev.dimi.erp.financeiro.PaymentMethod;
import dev.dimi.erp.financeiro.internal.application.FinancialPaymentSummary;
import dev.dimi.erp.identidade.UserId;
import java.time.Instant;
import java.util.List;

/** Response de pagamento com aplicações. */
public record FinancialPaymentApiResponse(
    PaymentId id,
    long version,
    BatchId batchId,
    Instant occurredAt,
    BankAccountId bankAccountId,
    PaymentMethod method,
    UserId userId,
    String notes,
    String idempotencyKey,
    Instant createdAt,
    List<PaymentApplicationApiResponse> applications) {

  public static FinancialPaymentApiResponse from(FinancialPaymentSummary s) {
    return new FinancialPaymentApiResponse(
        s.id(),
        s.version(),
        s.batchId(),
        s.occurredAt(),
        s.bankAccountId(),
        s.method(),
        s.userId(),
        s.notes(),
        s.idempotencyKey(),
        s.createdAt(),
        s.applications().stream().map(PaymentApplicationApiResponse::from).toList());
  }
}
