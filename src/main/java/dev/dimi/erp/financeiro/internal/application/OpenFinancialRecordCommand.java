package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Abre uma MVF com 1..N parcelas e rateios obrigatórios. Soma dos rateios deve bater {@code
 * totalValue}; soma das parcelas (original_value) também.
 *
 * <p>{@code idempotencyKey} opcional — quando presente, retorna a MVF existente sem efeitos
 * colaterais (RN-FIN-006). Para origens automáticas (SALE/PURCHASE/OS/CONTRACT), o par {@code
 * (originType, originId)} também é único — o service não duplica se já houver MVF para a mesma
 * origem.
 */
public record OpenFinancialRecordCommand(
    BranchId branchId,
    FinancialDirection direction,
    PersonId counterpartyId,
    String documentNumber,
    BigDecimal totalValue,
    FinancialOriginType originType,
    UUID originId,
    String notes,
    String idempotencyKey,
    List<InstallmentLine> installments,
    List<ApportionmentLine> apportionments,
    UserId actor) {

  public record InstallmentLine(
      LocalDate dueDate, BigDecimal originalValue, BankAccountId bankAccountId, String barcode) {}

  public record ApportionmentLine(
      ChartAccountId chartAccountId,
      CostCenterId costCenterId,
      BigDecimal value,
      BigDecimal percentage,
      String notes) {}
}
