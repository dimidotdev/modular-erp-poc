package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.fiscal.InvoiceAuthorizedEvent;
import dev.dimi.erp.fiscal.InvoiceRejectedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code FISCAL} disparados pelo módulo {@code fiscal}.
 *
 * <p>NFe autorizada cai em {@code INFO}; rejeição cai em {@code WARN} — operador precisa intervir
 * (retry/correção/CCe) mas não é alerta crítico. Cancelamento (slice 8.6) entra como {@code
 * CRITICAL} no futuro.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class FiscalAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(InvoiceAuthorizedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("invoiceId", event.invoiceId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("model", event.model().name());
      payload.put("series", event.series());
      payload.put("number", event.number());
      payload.put("fiscalKey", event.fiscalKey());
      payload.put("originType", event.originType().name());
      payload.put("originId", event.originId() == null ? null : event.originId().toString());
      payload.put("total", event.total() == null ? null : event.total().toPlainString());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.authorizedBy() == null ? null : event.authorizedBy().value(),
              null,
              event.occurredAt(),
              "INVOICE_AUTHORIZED",
              AuditCategory.FISCAL,
              AuditSeverity.INFO,
              "invoice",
              event.invoiceId().value().toString(),
              null,
              null,
              null,
              "NFe "
                  + event.fiscalKey()
                  + " autorizada (série "
                  + event.series()
                  + " nº "
                  + event.number()
                  + ")",
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit INVOICE_AUTHORIZED invoiceId={}", event.invoiceId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(InvoiceRejectedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("invoiceId", event.invoiceId().value().toString());
      payload.put("branchId", event.branchId().value().toString());
      payload.put("model", event.model().name());
      payload.put("series", event.series());
      payload.put("number", event.number());
      payload.put("originType", event.originType().name());
      payload.put("originId", event.originId() == null ? null : event.originId().toString());
      payload.put("rejectionCode", event.rejectionCode());
      payload.put("rejectionReason", event.rejectionReason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.attemptedBy() == null ? null : event.attemptedBy().value(),
              null,
              event.occurredAt(),
              "INVOICE_REJECTED",
              AuditCategory.FISCAL,
              AuditSeverity.WARN,
              "invoice",
              event.invoiceId().value().toString(),
              null,
              null,
              null,
              "NFe rejeitada — código " + event.rejectionCode() + ": " + event.rejectionReason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit INVOICE_REJECTED invoiceId={}", event.invoiceId().value(), e);
    }
  }
}
