package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.internal.domain.ApportionmentId;
import dev.dimi.erp.financeiro.internal.domain.FinancialApportionment;
import java.math.BigDecimal;

/** Snapshot de rateio. */
public record FinancialApportionmentSummary(
    ApportionmentId id,
    FinancialRecordId recordId,
    ChartAccountId chartAccountId,
    CostCenterId costCenterId,
    BigDecimal value,
    BigDecimal percentage,
    String notes) {

  public static FinancialApportionmentSummary of(FinancialApportionment a) {
    return new FinancialApportionmentSummary(
        a.id(),
        a.recordIdTyped(),
        a.chartAccountIdTyped(),
        a.costCenterIdTyped(),
        a.getValue(),
        a.getPercentage(),
        a.getNotes());
  }
}
