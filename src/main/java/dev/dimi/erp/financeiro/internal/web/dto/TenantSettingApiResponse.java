package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.internal.application.TenantSettingSummary;
import dev.dimi.erp.financeiro.internal.domain.TenantSettingId;
import dev.dimi.erp.identidade.UserId;
import java.time.Instant;

/** Response da configuração do tenant. */
public record TenantSettingApiResponse(
    TenantSettingId id,
    ChartAccountId defaultRevenueChartAccountId,
    ChartAccountId defaultExpenseChartAccountId,
    CostCenterId defaultCostCenterId,
    UserId updatedBy,
    Instant createdAt,
    Instant updatedAt) {

  public static TenantSettingApiResponse from(TenantSettingSummary s) {
    return new TenantSettingApiResponse(
        s.id(),
        s.defaultRevenueChartAccountId(),
        s.defaultExpenseChartAccountId(),
        s.defaultCostCenterId(),
        s.updatedBy(),
        s.createdAt(),
        s.updatedAt());
  }
}
