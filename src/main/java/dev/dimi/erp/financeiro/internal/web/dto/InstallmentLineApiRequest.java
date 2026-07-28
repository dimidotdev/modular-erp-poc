package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.internal.application.OpenFinancialRecordCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Linha de parcela em {@code POST /api/v1/financeiro/records}. */
public record InstallmentLineApiRequest(
    @NotNull LocalDate dueDate,
    @NotNull @Positive BigDecimal originalValue,
    UUID bankAccountId,
    @Size(max = 100) String barcode) {

  public OpenFinancialRecordCommand.InstallmentLine toCommand() {
    return new OpenFinancialRecordCommand.InstallmentLine(
        dueDate,
        originalValue,
        bankAccountId == null ? null : new BankAccountId(bankAccountId),
        barcode);
  }
}
