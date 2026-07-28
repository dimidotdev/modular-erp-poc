package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.identidade.UserCreatedEvent;
import dev.dimi.erp.identidade.UserDeactivatedEvent;
import dev.dimi.erp.identidade.UserReactivatedEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code ADMIN} relacionados à gestão de usuários no tenant.
 *
 * <p>Roles assigned/revoked, role created/edited, permission granted/revoked entram nos próximos
 * slices (operações de gestão de role). Aqui cobrimos só o ciclo de vida do usuário, suficiente
 * para a Etapa 9 da identidade.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class AdminAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(UserCreatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("username", event.username());
      payload.put("email", event.email());
      payload.put("roles", event.assignedRoles());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.createdBy() == null ? null : event.createdBy().value(),
              null,
              event.occurredAt(),
              "USER_CREATED",
              AuditCategory.ADMIN,
              AuditSeverity.INFO,
              "user",
              event.userId().value().toString(),
              null,
              null,
              null,
              "Usuário " + event.username() + " criado em " + event.tenantSlug(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit USER_CREATED userId={}", event.userId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(UserDeactivatedEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.deactivatedBy() == null ? null : event.deactivatedBy().value(),
              null,
              event.occurredAt(),
              "USER_DEACTIVATED",
              AuditCategory.ADMIN,
              AuditSeverity.INFO,
              "user",
              event.userId().value().toString(),
              null,
              null,
              null,
              "Usuário desativado: " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit USER_DEACTIVATED userId={}", event.userId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(UserReactivatedEvent event) {
    try {
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.reactivatedBy() == null ? null : event.reactivatedBy().value(),
              null,
              event.occurredAt(),
              "USER_REACTIVATED",
              AuditCategory.ADMIN,
              AuditSeverity.INFO,
              "user",
              event.userId().value().toString(),
              null,
              null,
              null,
              "Usuário reativado",
              null));
    } catch (RuntimeException e) {
      log.error("falha gravando audit USER_REACTIVATED userId={}", event.userId().value(), e);
    }
  }
}
