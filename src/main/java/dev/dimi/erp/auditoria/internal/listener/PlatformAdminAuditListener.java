package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.identidade.TenantProvisionedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code PLATFORM_ADMIN}.
 *
 * <p>Cobre por enquanto apenas {@code TENANT_PROVISIONED} (slice identidade). {@code
 * TENANT_SUSPENDED}, {@code TENANT_TERMINATED} e {@code IMPERSONATION_*} entram quando o módulo
 * Platform Admin for implementado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PlatformAdminAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(TenantProvisionedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("slug", event.tenantSlug());
      payload.put("schemaName", event.schemaName());
      payload.put("ownerUsername", event.ownerUsername());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              null, // ator é o operador de plataforma; não temos userId — vem via bootstrap token
              null,
              event.occurredAt(),
              "TENANT_PROVISIONED",
              AuditCategory.PLATFORM_ADMIN,
              AuditSeverity.INFO,
              "tenant",
              event.tenantId().value().toString(),
              null,
              null,
              null,
              "Tenant provisionado: " + event.tenantSlug(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit TENANT_PROVISIONED tenantId={}", event.tenantId().value(), e);
    }
  }
}
