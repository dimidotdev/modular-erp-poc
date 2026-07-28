package dev.dimi.erp.financeiro.internal.listener;

import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialListenerSkippedEvent;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.internal.application.ManageFinancialRecordService;
import dev.dimi.erp.financeiro.internal.application.ManageTenantSettingService;
import dev.dimi.erp.financeiro.internal.application.OpenFinancialRecordCommand;
import dev.dimi.erp.financeiro.internal.domain.TenantSetting;
import dev.dimi.erp.ordemservico.OsBilledEvent;
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
 * Cria MVF CREDIT (a receber) a partir de {@link OsBilledEvent}.
 *
 * <p>Limitações atuais, equivalentes às de {@link SaleClosedFinancialListener}:
 *
 * <ul>
 *   <li>1 parcela única com vencimento {@code occurredAt + 30d} — listener ignora {@code
 *       paymentConditionId} enquanto o parcelamento por condição não estiver implementado.
 *   <li>1 rateio com {@code chart_account_id = tenant_setting.default_revenue_chart_account_id} e
 *       {@code cost_center_id = tenant_setting.default_cost_center_id}.
 *   <li>Se setting ausente, publica {@link FinancialListenerSkippedEvent} e sai sem criar MVF.
 * </ul>
 *
 * <p>Idempotência: {@link ManageFinancialRecordService#open} dedup por par {@code (origin_type=OS,
 * origin_id=billingId)} — re-entrega do evento via outbox não duplica MVF.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OsBilledFinancialListener {

  private static final int DEFAULT_DUE_DAYS = 30;

  /**
   * Timezone padrão para cálculo de vencimento. Uma configuração por tenant deve substituir este
   * valor quando disponível.
   */
  private static final ZoneId BRAZIL_TZ = ZoneId.of("America/Sao_Paulo");

  private final ManageFinancialRecordService records;
  private final ManageTenantSettingService settings;
  private final ApplicationEventPublisher events;

  @ApplicationModuleListener
  void on(OsBilledEvent event) {
    TenantSetting setting = settings.loadOrCreatePending();
    if (setting.getDefaultRevenueChartAccountId() == null) {
      log.warn(
          "os-billed financial listener SKIPPED — tenant_setting.default_revenue_chart_account_id missing (billing={} os={})",
          event.billingId().value(),
          event.osId().value());
      events.publishEvent(
          new FinancialListenerSkippedEvent(
              event.tenantId(),
              "OsBilledEvent",
              event.billingId().value(),
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
            FinancialOriginType.OS,
            event.billingId().value(),
            "MVF gerada automaticamente a partir de faturamento de OS " + event.osId().value(),
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
            event.issuedBy());

    records.open(cmd);
    log.info(
        "os-billed financial listener created/dedup MVF billing={} os={} total={}",
        event.billingId().value(),
        event.osId().value(),
        event.total());
  }
}
