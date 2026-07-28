package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.InstallmentId;
import dev.dimi.erp.financeiro.InstallmentStatus;
import dev.dimi.erp.financeiro.internal.domain.FinancialInstallment;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Snapshot de parcela de MVF. */
public record FinancialInstallmentSummary(
    InstallmentId id,
    FinancialRecordId recordId,
    int sequence,
    LocalDate dueDate,
    BigDecimal originalValue,
    BigDecimal paidValue,
    BigDecimal fineValue,
    BigDecimal interestValue,
    BigDecimal discountValue,
    InstallmentStatus status,
    BankAccountId bankAccountId,
    String barcode) {

  public static FinancialInstallmentSummary of(FinancialInstallment i) {
    return new FinancialInstallmentSummary(
        i.id(),
        i.recordIdTyped(),
        i.getSequence(),
        i.getDueDate(),
        i.getOriginalValue(),
        i.getPaidValue(),
        i.getFineValue(),
        i.getInterestValue(),
        i.getDiscountValue(),
        i.getStatus(),
        i.bankAccountIdTyped(),
        i.getBarcode());
  }
}
