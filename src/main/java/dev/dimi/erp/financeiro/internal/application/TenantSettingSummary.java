package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.internal.domain.TenantSetting;
import dev.dimi.erp.financeiro.internal.domain.TenantSettingId;
import dev.dimi.erp.identidade.UserId;
import java.time.Instant;

/** Snapshot da configuração do tenant. */
public record TenantSettingSummary(
    TenantSettingId id,
    ChartAccountId defaultRevenueChartAccountId,
    ChartAccountId defaultExpenseChartAccountId,
    CostCenterId defaultCostCenterId,
    UserId updatedBy,
    Instant createdAt,
    Instant updatedAt) {

  public static TenantSettingSummary of(TenantSetting s) {
    return new TenantSettingSummary(
        s.id(),
        s.defaultRevenueChartAccountIdTyped(),
        s.defaultExpenseChartAccountIdTyped(),
        s.defaultCostCenterIdTyped(),
        s.updatedByTyped(),
        s.getCreatedAt(),
        s.getUpdatedAt());
  }
}
