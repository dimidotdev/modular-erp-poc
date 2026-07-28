package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.identidade.UserId;

/**
 * Upsert da configuração do tenant. Singleton — 1 row por schema; service garante via {@code
 * findSingleton()} antes de criar.
 *
 * <p>Qualquer ID nulo significa "desconfigurar este default". Setar tudo nulo é válido
 * (efetivamente "desabilita" os listeners de financeiro até reconfigurar).
 */
public record UpdateTenantSettingCommand(
    ChartAccountId defaultRevenueChartAccountId,
    ChartAccountId defaultExpenseChartAccountId,
    CostCenterId defaultCostCenterId,
    UserId actor) {}
