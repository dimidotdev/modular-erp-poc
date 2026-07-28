package dev.dimi.erp.financeiro.internal.listener;

import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialListenerSkippedEvent;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.internal.application.ManageFinancialRecordService;
import dev.dimi.erp.financeiro.internal.application.ManageTenantSettingService;
import dev.dimi.erp.financeiro.internal.application.OpenFinancialRecordCommand;
import dev.dimi.erp.financeiro.internal.domain.TenantSetting;
import dev.dimi.erp.movimentacao.SaleClosedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Cria MVF CREDIT (a receber) a partir de {@link SaleClosedEvent}.
 *
 * <p>Limitações v1 idênticas ao {@link PurchaseReceivedFinancialListener}: 1 parcela 30d + 1 rateio
 * com {@code default_revenue_chart_account_id}. Idempotência via par {@code (origin_type=SALE,
 * origin_id=saleId)}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SaleClosedFinancialListener {

  private static final int DEFAULT_DUE_DAYS = 30;

  /**
   * v1: timezone hardcoded para SP. Quando tenant_setting.time_zone existir (slice futuro), trocar
   * por lookup por tenant.
   */
  private static final ZoneId BRAZIL_TZ = ZoneId.of("America/Sao_Paulo");

  private final ManageFinancialRecordService records;
  private final ManageTenantSettingService settings;
  private final ApplicationEventPublisher events;

  @ApplicationModuleListener
  void on(SaleClosedEvent event) {
    TenantSetting setting = settings.loadOrCreatePending();
    if (setting.getDefaultRevenueChartAccountId() == null) {
      log.warn(
          "sale-closed financial listener SKIPPED — tenant_setting.default_revenue_chart_account_id missing (sale={})",
          event.saleId().value());
      events.publishEvent(
          new FinancialListenerSkippedEvent(
              event.tenantId(),
              "SaleClosedEvent",
              event.saleId().value(),
              "missing-default-revenue-chart-account",
              "tenant_setting.default_revenue_chart_account_id é null — cadastre via PUT /api/v1/financeiro/settings.",
              Instant.now()));
      return;
    }

    LocalDate dueDate =
        event.occurredAt().atZone(BRAZIL_TZ).toLocalDate().plusDays(DEFAULT_DUE_DAYS);

    OpenFinancialRecordCommand cmd =
        new OpenFinancialRecordCommand(
            event.branchId(),
            FinancialDirection.CREDIT,
            event.customerId(),
            null,
            event.total(),
            FinancialOriginType.SALE,
            event.saleId().value(),
            "MVF gerada automaticamente a partir de venda fechada " + event.saleId().value(),
            null,
            List.of(
                new OpenFinancialRecordCommand.InstallmentLine(dueDate, event.total(), null, null)),
            List.of(
                new OpenFinancialRecordCommand.ApportionmentLine(
                    setting.defaultRevenueChartAccountIdTyped(),
                    setting.defaultCostCenterIdTyped(),
                    event.total(),
                    BigDecimal.ONE,
                    null)),
            event.closedBy());

    records.open(cmd);
    log.info(
        "sale-closed financial listener created/dedup MVF sale={} total={}",
        event.saleId().value(),
        event.total());
  }
}
