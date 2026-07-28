package dev.dimi.erp.financeiro.internal.listener;

import dev.dimi.erp.contratos.ContractInvoiceGeneratedEvent;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialListenerSkippedEvent;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.internal.application.ManageFinancialRecordService;
import dev.dimi.erp.financeiro.internal.application.ManageTenantSettingService;
import dev.dimi.erp.financeiro.internal.application.OpenFinancialRecordCommand;
import dev.dimi.erp.financeiro.internal.domain.TenantSetting;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Cria MVF CREDIT (a receber) a partir de {@link ContractInvoiceGeneratedEvent}.
 *
 * <p>Limitações atuais:
 *
 * <ul>
 *   <li>1 parcela única com {@code dueDate} já calculado pelo módulo {@code contratos} a partir do
 *       {@code billingDay} do contrato. {@code paymentConditionId} do evento é ignorado por
 *       enquanto. Quando o parcelamento por condição de pagamento for suportado, este listener
 *       deverá honrar o plano calculado. Fallback de 30 dias não se aplica aqui: o contrato sempre
 *       fornece {@code dueDate}.
 *   <li>1 rateio com {@code chart_account_id = tenant_setting.default_revenue_chart_account_id} e
 *       {@code cost_center_id} preferindo {@code event.costCenterId()} (quando presente) ou caindo
 *       no default do tenant_setting.
 *   <li>Se {@code default_revenue_chart_account_id} ausente, publica {@link
 *       FinancialListenerSkippedEvent} e sai sem criar MVF.
 * </ul>
 *
 * <p>Idempotência: {@link ManageFinancialRecordService#open} dedup por par {@code
 * (origin_type=CONTRACT, origin_id=cycleId)} — re-entrega via outbox não duplica MVF, e ciclos
 * subsequentes do mesmo contrato têm {@code cycleId} distintos, gerando novas MVFs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ContractInvoiceGeneratedFinancialListener {

  private final ManageFinancialRecordService records;
  private final ManageTenantSettingService settings;
  private final ApplicationEventPublisher events;

  @ApplicationModuleListener
  void on(ContractInvoiceGeneratedEvent event) {
    TenantSetting setting = settings.loadOrCreatePending();
    if (setting.getDefaultRevenueChartAccountId() == null) {
      log.warn(
          "contract-invoice-generated financial listener SKIPPED — tenant_setting.default_revenue_chart_account_id missing (cycle={} contract={})",
          event.cycleId().value(),
          event.contractId().value());
      events.publishEvent(
          new FinancialListenerSkippedEvent(
              event.tenantId(),
              "ContractInvoiceGeneratedEvent",
              event.cycleId().value(),
              "missing-default-revenue-chart-account",
              "tenant_setting.default_revenue_chart_account_id é null — cadastre via PUT /api/v1/financeiro/settings.",
              Instant.now()));
      return;
    }

    CostCenterId costCenterId =
        event.costCenterId() != null
            ? new CostCenterId(event.costCenterId())
            : setting.defaultCostCenterIdTyped();

    OpenFinancialRecordCommand cmd =
        new OpenFinancialRecordCommand(
            event.branchId(),
            FinancialDirection.CREDIT,
            event.customerId(),
            null,
            event.value(),
            FinancialOriginType.CONTRACT,
            event.cycleId().value(),
            "MVF gerada automaticamente a partir de contrato "
                + event.contractTitle()
                + " — competência "
                + event.competenceMonth(),
            null,
            List.of(
                new OpenFinancialRecordCommand.InstallmentLine(
                    event.dueDate(), event.value(), null, null)),
            List.of(
                new OpenFinancialRecordCommand.ApportionmentLine(
                    setting.defaultRevenueChartAccountIdTyped(),
                    costCenterId,
                    event.value(),
                    BigDecimal.ONE,
                    null)),
            event.triggeredBy());

    records.open(cmd);
    log.info(
        "contract-invoice-generated financial listener created/dedup MVF cycle={} contract={} value={}",
        event.cycleId().value(),
        event.contractId().value(),
        event.value());
  }
}
