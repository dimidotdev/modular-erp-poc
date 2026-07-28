package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.internal.application.RenegotiateFinancialRecordCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Linha de parcela em renegociação. */
public record NewInstallmentLineApiRequest(
    @NotNull LocalDate dueDate,
    @NotNull @Positive BigDecimal originalValue,
    UUID bankAccountId,
    @Size(max = 100) String barcode) {

  public RenegotiateFinancialRecordCommand.NewInstallmentLine toCommand() {
    return new RenegotiateFinancialRecordCommand.NewInstallmentLine(
        dueDate,
        originalValue,
        bankAccountId == null ? null : new BankAccountId(bankAccountId),
        barcode);
  }
}
