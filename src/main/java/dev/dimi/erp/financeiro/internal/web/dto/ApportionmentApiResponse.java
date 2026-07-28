package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.internal.application.FinancialApportionmentSummary;
import dev.dimi.erp.financeiro.internal.domain.ApportionmentId;
import java.math.BigDecimal;

/** Response de rateio. */
public record ApportionmentApiResponse(
    ApportionmentId id,
    FinancialRecordId recordId,
    ChartAccountId chartAccountId,
    CostCenterId costCenterId,
    BigDecimal value,
    BigDecimal percentage,
    String notes) {

  public static ApportionmentApiResponse from(FinancialApportionmentSummary s) {
    return new ApportionmentApiResponse(
        s.id(),
        s.recordId(),
        s.chartAccountId(),
        s.costCenterId(),
        s.value(),
        s.percentage(),
        s.notes());
  }
}
