package dev.dimi.erp.financeiro.internal.application;

public record FinancialPaymentApplicationSummary(
    dev.dimi.erp.financeiro.internal.domain.PaymentApplicationId id,
    dev.dimi.erp.financeiro.PaymentId paymentId,
    dev.dimi.erp.financeiro.InstallmentId installmentId,
    java.math.BigDecimal value,
    java.math.BigDecimal fineApplied,
    java.math.BigDecimal interestApplied,
    java.math.BigDecimal discountApplied) {

  public static FinancialPaymentApplicationSummary of(
      dev.dimi.erp.financeiro.internal.domain.FinancialPaymentApplication a) {
    return new FinancialPaymentApplicationSummary(
        a.id(),
        a.paymentIdTyped(),
        a.installmentIdTyped(),
        a.getValue(),
        a.getFineApplied(),
        a.getInterestApplied(),
        a.getDiscountApplied());
  }
}
