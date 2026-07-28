package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.cadastros.PersonAnonymizedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code LGPD} disparados pelo módulo {@code cadastros}.
 *
 * <p>Em v1 cobrimos apenas {@link PersonAnonymizedEvent} (RN-CAD-011/012) com severidade {@code
 * CRITICAL} — operação irreversível com retenção legal estendida (10 anos), exige rastro destacado
 * no painel de auditoria. Eventos de consentimento e exportação de dados pessoais entram quando o
 * slice LGPD pleno for desenvolvido.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class LgpdAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(PersonAnonymizedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("personId", event.personId().value().toString());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.anonymizedBy() == null ? null : event.anonymizedBy().value(),
              null,
              event.occurredAt(),
              "PERSON_ANONYMIZED",
              AuditCategory.LGPD,
              AuditSeverity.CRITICAL,
              "person",
              event.personId().value().toString(),
              null,
              null,
              null,
              "Pessoa anonimizada (LGPD): " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit PERSON_ANONYMIZED personId={}", event.personId().value(), e);
    }
  }
}
