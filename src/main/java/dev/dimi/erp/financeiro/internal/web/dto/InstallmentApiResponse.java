package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.InstallmentId;
import dev.dimi.erp.financeiro.InstallmentStatus;
import dev.dimi.erp.financeiro.internal.application.FinancialInstallmentSummary;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Response de parcela. */
public record InstallmentApiResponse(
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

  public static InstallmentApiResponse from(FinancialInstallmentSummary s) {
    return new InstallmentApiResponse(
        s.id(),
        s.recordId(),
        s.sequence(),
        s.dueDate(),
        s.originalValue(),
        s.paidValue(),
        s.fineValue(),
        s.interestValue(),
        s.discountValue(),
        s.status(),
        s.bankAccountId(),
        s.barcode());
  }
}
