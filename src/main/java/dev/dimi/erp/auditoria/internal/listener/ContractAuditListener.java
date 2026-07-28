package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.contratos.ContractActivatedEvent;
import dev.dimi.erp.contratos.ContractAdjustedEvent;
import dev.dimi.erp.contratos.ContractAmendedEvent;
import dev.dimi.erp.contratos.ContractCreatedEvent;
import dev.dimi.erp.contratos.ContractCycleFailedEvent;
import dev.dimi.erp.contratos.ContractInvoiceGeneratedEvent;
import dev.dimi.erp.contratos.ContractResumedEvent;
import dev.dimi.erp.contratos.ContractSuspendedEvent;
import dev.dimi.erp.contratos.ContractTerminatedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code CONTRACT} disparados pelo módulo {@code contratos}.
 *
 * <p>Rescisão antecipada ({@code earlyTermination=true}) escala para {@code CRITICAL} — gera multa
 * e merece alerta humano. Suspensão, rescisão normal e falha de ciclo ficam em {@code WARN}; demais
 * eventos em {@code INFO}. Reativação (resume) é registrada como {@code CONTRACT_ACTIVATED} para
 * uniformizar busca.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class ContractAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(ContractCreatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("contractId", event.id().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("customerId", event.customerId().value().toString());
      payload.put("number", event.number());
      payload.put(
          "baseValue", event.baseValue() == null ? null : event.baseValue().toPlainString());
      payload.put("recurrence", event.recurrence().name());
      payload.put("billingDay", event.billingDay());
      payload.put("startDate", event.startDate() == null ? null : event.startDate().toString());
      payload.put("endDate", event.endDate() == null ? null : event.endDate().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.createdBy() == null ? null : event.createdBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_CREATED",
              AuditCategory.CONTRACT,
              AuditSeverity.INFO,
              "contract",
              event.id().value().toString(),
              null,
              null,
              null,
              "Contrato " + event.number() + " criado",
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit CONTRACT_CREATED contractId={}", event.id().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(ContractActivatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("contractId", event.id().value().toString());
      payload.put("startDate", event.startDate() == null ? null : event.startDate().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.activatedBy() == null ? null : event.activatedBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_ACTIVATED",
              AuditCategory.CONTRACT,
              AuditSeverity.INFO,
              "contract",
              event.id().value().toString(),
              null,
              null,
              null,
              "Contrato ativado",
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit CONTRACT_ACTIVATED contractId={}", event.id().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(ContractSuspendedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("contractId", event.id().value().toString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.suspendedBy() == null ? null : event.suspendedBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_SUSPENDED",
              AuditCategory.CONTRACT,
              AuditSeverity.WARN,
              "contract",
              event.id().value().toString(),
              null,
              null,
              null,
              "Contrato suspenso: " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit CONTRACT_SUSPENDED contractId={}", event.id().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(ContractResumedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("contractId", event.id().value().toString());
      payload.put("resumed", true);
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.resumedBy() == null ? null : event.resumedBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_ACTIVATED",
              AuditCategory.CONTRACT,
              AuditSeverity.INFO,
              "contract",
              event.id().value().toString(),
              null,
              null,
              null,
              "Contrato retomado",
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit CONTRACT_ACTIVATED (resume) contractId={}", event.id().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(ContractTerminatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("contractId", event.id().value().toString());
      payload.put(
          "terminationDate",
          event.terminationDate() == null ? null : event.terminationDate().toString());
      payload.put("reason", event.reason());
      payload.put(
          "cancellationFee",
          event.cancellationFee() == null ? null : event.cancellationFee().toPlainString());
      payload.put("earlyTermination", event.earlyTermination());
      AuditSeverity severity =
          event.earlyTermination() ? AuditSeverity.CRITICAL : AuditSeverity.WARN;
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.terminatedBy() == null ? null : event.terminatedBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_TERMINATED",
              AuditCategory.CONTRACT,
              severity,
              "contract",
              event.id().value().toString(),
              null,
              null,
              null,
              "Contrato encerrado ("
                  + (event.earlyTermination() ? "antecipado" : "normal")
                  + "): "
                  + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit CONTRACT_TERMINATED contractId={}", event.id().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(ContractAmendedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("amendmentId", event.amendmentId().value().toString());
      payload.put("contractId", event.contractId().value().toString());
      payload.put("type", event.type().name());
      payload.put(
          "effectiveFrom", event.effectiveFrom() == null ? null : event.effectiveFrom().toString());
      payload.put(
          "newBaseValue",
          event.newBaseValue() == null ? null : event.newBaseValue().toPlainString());
      payload.put("newEndDate", event.newEndDate() == null ? null : event.newEndDate().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.effectivatedBy() == null ? null : event.effectivatedBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_AMENDED",
              AuditCategory.CONTRACT,
              AuditSeverity.INFO,
              "contract",
              event.contractId().value().toString(),
              null,
              null,
              null,
              "Aditivo contratual " + event.type().name() + " efetivado",
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit CONTRACT_AMENDED amendmentId={}", event.amendmentId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(ContractAdjustedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("adjustmentId", event.adjustmentId().value().toString());
      payload.put("contractId", event.contractId().value().toString());
      payload.put("index", event.index().name());
      payload.put(
          "percentage", event.percentage() == null ? null : event.percentage().toPlainString());
      payload.put(
          "oldBaseValue",
          event.oldBaseValue() == null ? null : event.oldBaseValue().toPlainString());
      payload.put(
          "newBaseValue",
          event.newBaseValue() == null ? null : event.newBaseValue().toPlainString());
      payload.put("appliedOn", event.appliedOn() == null ? null : event.appliedOn().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.appliedBy() == null ? null : event.appliedBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_ADJUSTED",
              AuditCategory.CONTRACT,
              AuditSeverity.INFO,
              "contract",
              event.contractId().value().toString(),
              null,
              null,
              null,
              "Reajuste "
                  + event.index().name()
                  + " aplicado — "
                  + (event.percentage() == null ? "0" : event.percentage().toPlainString())
                  + "%",
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit CONTRACT_ADJUSTED adjustmentId={}",
          event.adjustmentId().value(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(ContractInvoiceGeneratedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("cycleId", event.cycleId().value().toString());
      payload.put("contractId", event.contractId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("customerId", event.customerId().value().toString());
      payload.put(
          "competenceMonth",
          event.competenceMonth() == null ? null : event.competenceMonth().toString());
      payload.put("dueDate", event.dueDate() == null ? null : event.dueDate().toString());
      payload.put("value", event.value() == null ? null : event.value().toPlainString());
      payload.put("contractTitle", event.contractTitle());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.triggeredBy() == null ? null : event.triggeredBy().value(),
              null,
              event.occurredAt(),
              "CONTRACT_INVOICE_GENERATED",
              AuditCategory.CONTRACT,
              AuditSeverity.INFO,
              "billing_cycle",
              event.cycleId().value().toString(),
              null,
              null,
              null,
              "Ciclo de faturamento gerado — valor "
                  + (event.value() == null ? "0" : event.value().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit CONTRACT_INVOICE_GENERATED cycleId={}", event.cycleId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(ContractCycleFailedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("cycleId", event.cycleId().value().toString());
      payload.put("contractId", event.contractId().value().toString());
      payload.put(
          "competenceMonth",
          event.competenceMonth() == null ? null : event.competenceMonth().toString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              null,
              null,
              event.occurredAt(),
              "CONTRACT_CYCLE_FAILED",
              AuditCategory.CONTRACT,
              AuditSeverity.WARN,
              "billing_cycle",
              event.cycleId().value().toString(),
              null,
              null,
              null,
              "Ciclo de faturamento falhou: " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit CONTRACT_CYCLE_FAILED cycleId={}", event.cycleId().value(), e);
    }
  }
}
