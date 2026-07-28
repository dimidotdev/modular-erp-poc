package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.internal.application.UpdateTenantSettingCommand;
import dev.dimi.erp.identidade.UserId;
import java.util.UUID;

/**
 * Payload de {@code PUT /api/v1/financeiro/settings}. Qualquer campo nulo desconfigura o default
 * correspondente; setar tudo nulo é válido.
 */
public record UpdateTenantSettingApiRequest(
    UUID defaultRevenueChartAccountId,
    UUID defaultExpenseChartAccountId,
    UUID defaultCostCenterId) {

  public UpdateTenantSettingCommand toCommand(UserId actor) {
    return new UpdateTenantSettingCommand(
        defaultRevenueChartAccountId == null
            ? null
            : new ChartAccountId(defaultRevenueChartAccountId),
        defaultExpenseChartAccountId == null
            ? null
            : new ChartAccountId(defaultExpenseChartAccountId),
        defaultCostCenterId == null ? null : new CostCenterId(defaultCostCenterId),
        actor);
  }
}
