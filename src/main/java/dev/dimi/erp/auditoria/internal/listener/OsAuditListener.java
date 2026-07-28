package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.ordemservico.LaborAppointedEvent;
import dev.dimi.erp.ordemservico.LaborFinishedEvent;
import dev.dimi.erp.ordemservico.OsBilledEvent;
import dev.dimi.erp.ordemservico.OsBillingCancelledEvent;
import dev.dimi.erp.ordemservico.OsCancelledEvent;
import dev.dimi.erp.ordemservico.OsClosedEvent;
import dev.dimi.erp.ordemservico.OsOpenedEvent;
import dev.dimi.erp.ordemservico.OsProductAddedEvent;
import dev.dimi.erp.ordemservico.OsProductConsumedEvent;
import dev.dimi.erp.ordemservico.OsServiceAddedEvent;
import dev.dimi.erp.ordemservico.OsStatusChangedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code OS} disparados pelo módulo {@code ordemservico}.
 *
 * <p>Cancelamentos (OS e faturamento) sobem para {@code WARN} — exigem revisão posterior. {@link
 * OsProductConsumedEvent} fica em categoria {@code STOCK} porque o efeito auditável é a baixa de
 * estoque (apesar de originar na OS); demais eventos ficam em {@code OS}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class OsAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(OsOpenedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("osId", event.osId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("customerId", event.customerId().value().toString());
      payload.put("number", event.number());
      payload.put("priority", event.priority().name());
      payload.put("assignedTo", event.assignedTo() == null ? null : event.assignedTo().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.createdBy() == null ? null : event.createdBy().value(),
              null,
              event.occurredAt(),
              "OS_OPENED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "service_order",
              event.osId().value().toString(),
              null,
              null,
              null,
              "OS " + event.number() + " aberta",
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_OPENED osId={}", event.osId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsStatusChangedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("osId", event.osId().value().toString());
      payload.put("fromStatusId", event.fromStatusId().value().toString());
      payload.put("fromCode", event.fromCode());
      payload.put("toStatusId", event.toStatusId().value().toString());
      payload.put("toCode", event.toCode());
      payload.put("notes", event.notes());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.changedBy() == null ? null : event.changedBy().value(),
              null,
              event.occurredAt(),
              "OS_STATUS_CHANGED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "service_order",
              event.osId().value().toString(),
              null,
              null,
              null,
              "OS status " + event.fromCode() + " -> " + event.toCode(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_STATUS_CHANGED osId={}", event.osId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsClosedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("osId", event.osId().value().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.closedBy() == null ? null : event.closedBy().value(),
              null,
              event.occurredAt(),
              "OS_CLOSED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "service_order",
              event.osId().value().toString(),
              null,
              null,
              null,
              "OS encerrada",
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_CLOSED osId={}", event.osId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsCancelledEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("osId", event.osId().value().toString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.cancelledBy() == null ? null : event.cancelledBy().value(),
              null,
              event.occurredAt(),
              "OS_CANCELLED",
              AuditCategory.OS,
              AuditSeverity.WARN,
              "service_order",
              event.osId().value().toString(),
              null,
              null,
              null,
              "OS cancelada: " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_CANCELLED osId={}", event.osId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsBilledEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("billingId", event.billingId().value().toString());
      payload.put("osId", event.osId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("customerId", event.customerId().value().toString());
      payload.put(
          "nfeInvoiceId", event.nfeInvoiceId() == null ? null : event.nfeInvoiceId().toString());
      payload.put(
          "nfseInvoiceId", event.nfseInvoiceId() == null ? null : event.nfseInvoiceId().toString());
      payload.put(
          "productsAmount",
          event.productsAmount() == null ? null : event.productsAmount().toPlainString());
      payload.put(
          "servicesAmount",
          event.servicesAmount() == null ? null : event.servicesAmount().toPlainString());
      payload.put("total", event.total() == null ? null : event.total().toPlainString());
      payload.put(
          "paymentConditionId",
          event.paymentConditionId() == null ? null : event.paymentConditionId().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.issuedBy() == null ? null : event.issuedBy().value(),
              null,
              event.occurredAt(),
              "OS_BILLED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "service_order_billing",
              event.billingId().value().toString(),
              null,
              null,
              null,
              "OS faturada — total "
                  + (event.total() == null ? "0" : event.total().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_BILLED billingId={}", event.billingId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsBillingCancelledEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("billingId", event.billingId().value().toString());
      payload.put("osId", event.osId().value().toString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.cancelledBy() == null ? null : event.cancelledBy().value(),
              null,
              event.occurredAt(),
              "OS_BILLING_CANCELLED",
              AuditCategory.OS,
              AuditSeverity.WARN,
              "service_order_billing",
              event.billingId().value().toString(),
              null,
              null,
              null,
              "Faturamento da OS cancelado: " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit OS_BILLING_CANCELLED billingId={}", event.billingId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsProductAddedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("productId", event.productId().value().toString());
      payload.put("osId", event.osId().value().toString());
      payload.put("packagingId", event.packagingId().value().toString());
      payload.put("quantity", event.quantity() == null ? null : event.quantity().toPlainString());
      payload.put("netValue", event.netValue() == null ? null : event.netValue().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.actor() == null ? null : event.actor().value(),
              null,
              event.occurredAt(),
              "OS_PRODUCT_ADDED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "os_product",
              event.productId().value().toString(),
              null,
              null,
              null,
              "Produto adicionado à OS — quantidade "
                  + (event.quantity() == null ? "0" : event.quantity().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_PRODUCT_ADDED productId={}", event.productId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsServiceAddedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("serviceId", event.serviceId().value().toString());
      payload.put("osId", event.osId().value().toString());
      payload.put("serviceCode", event.serviceCode());
      payload.put("netValue", event.netValue() == null ? null : event.netValue().toPlainString());
      payload.put(
          "issqnValue", event.issqnValue() == null ? null : event.issqnValue().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.actor() == null ? null : event.actor().value(),
              null,
              event.occurredAt(),
              "OS_SERVICE_ADDED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "os_service",
              event.serviceId().value().toString(),
              null,
              null,
              null,
              "Serviço adicionado à OS — código " + event.serviceCode(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_SERVICE_ADDED serviceId={}", event.serviceId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(LaborAppointedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("laborId", event.laborId().value().toString());
      payload.put("osId", event.osId().value().toString());
      payload.put("employeeId", event.employeeId().value().toString());
      payload.put("startedAt", event.startedAt() == null ? null : event.startedAt().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.actor() == null ? null : event.actor().value(),
              null,
              event.occurredAt(),
              "OS_LABOR_APPOINTED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "os_labor",
              event.laborId().value().toString(),
              null,
              null,
              null,
              "Apontamento iniciado para colaborador " + event.employeeId().value(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_LABOR_APPOINTED laborId={}", event.laborId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(LaborFinishedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("laborId", event.laborId().value().toString());
      payload.put("osId", event.osId().value().toString());
      payload.put("hours", event.hours() == null ? null : event.hours().toPlainString());
      payload.put("finishedAt", event.finishedAt() == null ? null : event.finishedAt().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.actor() == null ? null : event.actor().value(),
              null,
              event.occurredAt(),
              "OS_LABOR_FINISHED",
              AuditCategory.OS,
              AuditSeverity.INFO,
              "os_labor",
              event.laborId().value().toString(),
              null,
              null,
              null,
              "Apontamento finalizado — "
                  + (event.hours() == null ? "0" : event.hours().toPlainString())
                  + "h",
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit OS_LABOR_FINISHED laborId={}", event.laborId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(OsProductConsumedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("productId", event.productId().value().toString());
      payload.put("osId", event.osId().value().toString());
      payload.put("packagingId", event.packagingId().value().toString());
      payload.put("quantity", event.quantity() == null ? null : event.quantity().toPlainString());
      payload.put(
          "stockMovementId",
          event.stockMovementId() == null ? null : event.stockMovementId().toString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.actor() == null ? null : event.actor().value(),
              null,
              event.occurredAt(),
              "OUTBOUND_VOUCHER_ISSUED",
              AuditCategory.STOCK,
              AuditSeverity.INFO,
              "os_product",
              event.productId().value().toString(),
              null,
              null,
              null,
              "Produto consumido na OS — quantidade "
                  + (event.quantity() == null ? "0" : event.quantity().toPlainString()),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit OUTBOUND_VOUCHER_ISSUED productId={}",
          event.productId().value(),
          e);
    }
  }
}
