package dev.dimi.erp.financeiro.internal.listener;

import dev.dimi.erp.compras.PurchaseReceivedEvent;
import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialListenerSkippedEvent;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.internal.application.ManageFinancialRecordService;
import dev.dimi.erp.financeiro.internal.application.ManageTenantSettingService;
import dev.dimi.erp.financeiro.internal.application.OpenFinancialRecordCommand;
import dev.dimi.erp.financeiro.internal.domain.TenantSetting;
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
 * Cria MVF DEBIT (a pagar) a partir de {@link PurchaseReceivedEvent}.
 *
 * <p>Limitações atuais:
 *
 * <ul>
 *   <li>1 parcela única com vencimento {@code occurredAt + 30d} — listener não consome {@code
 *       paymentConditionId} ainda.
 *   <li>1 rateio com {@code chart_account_id = tenant_setting.default_expense_chart_account_id} e
 *       {@code cost_center_id = tenant_setting.default_cost_center_id}.
 *   <li>Se setting ausente, publica {@link FinancialListenerSkippedEvent} e sai sem criar MVF.
 * </ul>
 *
 * <p>Idempotência: {@code ManageFinancialRecordService.open} dedup por par {@code
 * (origin_type=PURCHASE, origin_id=receiptId)} — re-entrega do evento não duplica MVF.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PurchaseReceivedFinancialListener {

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
  void on(PurchaseReceivedEvent event) {
    TenantSetting setting = settings.loadOrCreatePending();
    if (setting.getDefaultExpenseChartAccountId() == null) {
      log.warn(
          "purchase-received financial listener SKIPPED — tenant_setting.default_expense_chart_account_id missing (receipt={})",
          event.receiptId().value());
      events.publishEvent(
          new FinancialListenerSkippedEvent(
              event.tenantId(),
              "PurchaseReceivedEvent",
              event.receiptId().value(),
              "missing-default-expense-chart-account",
              "tenant_setting.default_expense_chart_account_id é null — cadastre via PUT /api/v1/financeiro/settings.",
              Instant.now()));
      return;
    }

    LocalDate dueDate =
        event.occurredAt().atZone(BRAZIL_TZ).toLocalDate().plusDays(DEFAULT_DUE_DAYS);

    OpenFinancialRecordCommand cmd =
        new OpenFinancialRecordCommand(
            event.branchId(),
            FinancialDirection.DEBIT,
            event.supplierId(),
            null,
            event.total(),
            FinancialOriginType.PURCHASE,
            event.receiptId().value(),
            "MVF gerada automaticamente a partir de recebimento de compra "
                + event.receiptId().value(),
            null,
            List.of(
                new OpenFinancialRecordCommand.InstallmentLine(dueDate, event.total(), null, null)),
            List.of(
                new OpenFinancialRecordCommand.ApportionmentLine(
                    setting.defaultExpenseChartAccountIdTyped(),
                    setting.defaultCostCenterIdTyped(),
                    event.total(),
                    BigDecimal.ONE,
                    null)),
            event.receivedBy());

    records.open(cmd);
    log.info(
        "purchase-received financial listener created/dedup MVF receipt={} total={}",
        event.receiptId().value(),
        event.total());
  }
}
