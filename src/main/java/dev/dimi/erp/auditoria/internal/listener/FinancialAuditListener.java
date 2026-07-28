package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.financeiro.FinancialListenerSkippedEvent;
import dev.dimi.erp.financeiro.FinancialRecordCancelledEvent;
import dev.dimi.erp.financeiro.FinancialRecordCreatedEvent;
import dev.dimi.erp.financeiro.FinancialRecordRenegotiatedEvent;
import dev.dimi.erp.financeiro.PaymentProcessedEvent;
import dev.dimi.erp.financeiro.PaymentReversedEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code FINANCIAL} disparados pelo módulo {@code financeiro}.
 *
 * <p>Criação/recebimento são {@code INFO}; cancelamento, renegociação e estorno sobem para {@code
 * WARN} — operações sensíveis que mudam o saldo histórico. {@link FinancialListenerSkippedEvent}
 * entra como {@code SYSTEM} {@code WARN} porque é falha silenciosa de listener que merece
 * visibilidade no painel de auditoria (operador precisa regenerar MVF manualmente).
 */
@Component
@Slf4j
@RequiredArgsConstructor
class FinancialAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(FinancialRecordCreatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("recordId", event.recordId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("direction", event.direction().name());
      payload.put(
          "counterpartyId",
          event.counterpartyId() == null ? null : event.counterpartyId().value().toString());
      payload.put("originType", event.originType().name());
      payload.put("originId", event.originId() == null ? null : event.originId().toString());
      payload.put(
          "totalValue", event.totalValue() == null ? null : event.totalValue().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.createdBy() == null ? null : event.createdBy().value(),
              null,
              event.occurredAt(),
              "MVF_CREATED",
              AuditCategory.FINANCIAL,
              AuditSeverity.INFO,
              "financial_record",
              event.recordId().value().toString(),
              null,
              null,
              null,
              "MVF "
                  + event.direction().name()
                  + " criada — total "
                  + (event.totalValue() == null ? "0" : event.totalValue().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit MVF_CREATED recordId={}", event.recordId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(FinancialRecordCancelledEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("recordId", event.recordId().value().toString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.cancelledBy() == null ? null : event.cancelledBy().value(),
              null,
              event.occurredAt(),
              "MVF_CANCELLED",
              AuditCategory.FINANCIAL,
              AuditSeverity.WARN,
              "financial_record",
              event.recordId().value().toString(),
              null,
              null,
              null,
              "MVF cancelada: " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit MVF_CANCELLED recordId={}", event.recordId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(FinancialRecordRenegotiatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("originalRecordId", event.originalRecordId().value().toString());
      payload.put("replacementRecordId", event.replacementRecordId().value().toString());
      payload.put(
          "originalRemainingValue",
          event.originalRemainingValue() == null
              ? null
              : event.originalRemainingValue().toPlainString());
      payload.put(
          "replacementTotalValue",
          event.replacementTotalValue() == null
              ? null
              : event.replacementTotalValue().toPlainString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.renegotiatedBy() == null ? null : event.renegotiatedBy().value(),
              null,
              event.occurredAt(),
              "MVF_RENEGOTIATED",
              AuditCategory.FINANCIAL,
              AuditSeverity.WARN,
              "financial_record",
              event.originalRecordId().value().toString(),
              null,
              null,
              null,
              "MVF renegociada — substituta " + event.replacementRecordId().value(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit MVF_RENEGOTIATED originalRecordId={}",
          event.originalRecordId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(PaymentProcessedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("batchId", event.batchId().value().toString());
      payload.put("bankAccountId", event.bankAccountId().value().toString());
      payload.put("method", event.method().name());
      payload.put(
          "paymentIds",
          event.paymentIds() == null
              ? List.of()
              : event.paymentIds().stream().map(p -> p.value().toString()).toList());
      payload.put(
          "totalApplied",
          event.totalApplied() == null ? null : event.totalApplied().toPlainString());
      int count = event.paymentIds() == null ? 0 : event.paymentIds().size();
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.processedBy() == null ? null : event.processedBy().value(),
              null,
              event.occurredAt(),
              "PAYMENT_PROCESSED",
              AuditCategory.FINANCIAL,
              AuditSeverity.INFO,
              "payment_batch",
              event.batchId().value().toString(),
              null,
              null,
              null,
              "Batch de pagamento processado — "
                  + count
                  + " pagamento(s), total "
                  + (event.totalApplied() == null ? "0" : event.totalApplied().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit PAYMENT_PROCESSED batchId={}", event.batchId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(PaymentReversedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("reversalId", event.reversalId().toString());
      payload.put("originalPaymentId", event.originalPaymentId().value().toString());
      payload.put("reversalPaymentId", event.reversalPaymentId().value().toString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.reversedBy() == null ? null : event.reversedBy().value(),
              null,
              event.occurredAt(),
              "PAYMENT_REVERSED",
              AuditCategory.FINANCIAL,
              AuditSeverity.WARN,
              "payment",
              event.originalPaymentId().value().toString(),
              null,
              null,
              null,
              "Pagamento estornado: " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit PAYMENT_REVERSED originalPaymentId={}",
          event.originalPaymentId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(FinancialListenerSkippedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("upstreamEvent", event.upstreamEvent());
      payload.put("upstreamId", event.upstreamId() == null ? null : event.upstreamId().toString());
      payload.put("reasonCode", event.reasonCode());
      payload.put("reasonDetail", event.reasonDetail());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              null,
              null,
              event.occurredAt(),
              "FINANCIAL_LISTENER_SKIPPED",
              AuditCategory.SYSTEM,
              AuditSeverity.WARN,
              "financial_listener",
              event.upstreamId() == null ? null : event.upstreamId().toString(),
              null,
              null,
              null,
              "Listener financeiro pulou "
                  + event.upstreamEvent()
                  + " ("
                  + event.reasonCode()
                  + "): "
                  + event.reasonDetail(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit FINANCIAL_LISTENER_SKIPPED upstreamEvent={}",
          event.upstreamEvent(),
          e);
    }
  }
}
