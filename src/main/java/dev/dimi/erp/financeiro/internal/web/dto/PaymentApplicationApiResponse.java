package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.InstallmentId;
import dev.dimi.erp.financeiro.PaymentId;
import dev.dimi.erp.financeiro.internal.application.FinancialPaymentApplicationSummary;
import dev.dimi.erp.financeiro.internal.domain.PaymentApplicationId;
import java.math.BigDecimal;

/** Response de aplicação de pagamento. */
public record PaymentApplicationApiResponse(
    PaymentApplicationId id,
    PaymentId paymentId,
    InstallmentId installmentId,
    BigDecimal value,
    BigDecimal fineApplied,
    BigDecimal interestApplied,
    BigDecimal discountApplied) {

  public static PaymentApplicationApiResponse from(FinancialPaymentApplicationSummary s) {
    return new PaymentApplicationApiResponse(
        s.id(),
        s.paymentId(),
        s.installmentId(),
        s.value(),
        s.fineApplied(),
        s.interestApplied(),
        s.discountApplied());
  }
}
