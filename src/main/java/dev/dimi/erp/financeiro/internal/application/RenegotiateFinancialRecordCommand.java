package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Renegocia uma MVF: marca a original como {@code RENEGOTIATED} e gera uma MVF substituta com novas
 * parcelas. O saldo da nova MVF é informado pelo caller (pode ser igual ao remanescente da
 * original, ou maior se houver juros/encargos pactuados). Rateio e contraparte são herdados da
 * original — service não exige reinformar.
 */
public record RenegotiateFinancialRecordCommand(
    FinancialRecordId originalRecordId,
    BigDecimal newTotalValue,
    String reason,
    String notes,
    List<NewInstallmentLine> newInstallments,
    UserId actor) {

  public record NewInstallmentLine(
      LocalDate dueDate, BigDecimal originalValue, BankAccountId bankAccountId, String barcode) {}
}
