package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.internal.application.OpenFinancialRecordCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de rateio. {@code percentage} opcional (0 &lt; pct &le; 1). */
public record ApportionmentLineApiRequest(
    @NotNull UUID chartAccountId,
    UUID costCenterId,
    @NotNull @Positive BigDecimal value,
    @DecimalMin(value = "0.0001") @DecimalMax(value = "1.0000") BigDecimal percentage,
    @Size(max = 500) String notes) {

  public OpenFinancialRecordCommand.ApportionmentLine toCommand() {
    return new OpenFinancialRecordCommand.ApportionmentLine(
        new ChartAccountId(chartAccountId),
        costCenterId == null ? null : new CostCenterId(costCenterId),
        value,
        percentage,
        notes);
  }
}
