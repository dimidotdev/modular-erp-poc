package dev.dimi.erp.auditoria.internal.listener;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventRecord;
import dev.dimi.erp.auditoria.internal.application.AuditWriter;
import dev.dimi.erp.identidade.LoginFailedEvent;
import dev.dimi.erp.identidade.LoginSucceededEvent;
import dev.dimi.erp.identidade.SessionRevokedAllEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Persiste eventos da categoria {@code AUTH} disparados pelo módulo {@code identidade}.
 *
 * <p>Cada método é um {@link ApplicationModuleListener} — async, em transação separada, com
 * publicação durável via outbox. Falhas de gravação só logam; o Modulith outbox cuida do retry.
 *
 * <p>{@code LoginFailedEvent} com {@code tenantSlug} nulo (slug inválido) é descartado: sem {@code
 * tenantId} não há chave para escrever em {@code audit.audit_event} (que exige NOT NULL). Esses
 * casos extremos ficam no log de aplicação.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class AuthAuditListener {

  private final AuditWriter writer;

  @ApplicationModuleListener
  void on(LoginSucceededEvent event) {
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("sessionId", event.sessionId().value().toString());
      payload.put("amr", event.amr());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.userId().value(),
              event.sessionId().value(),
              event.occurredAt(),
              "LOGIN_SUCCEEDED",
              AuditCategory.AUTH,
              AuditSeverity.INFO,
              "user",
              event.userId().value().toString(),
              event.ip(),
              event.userAgent(),
              null,
              "Login bem-sucedido em " + event.tenantSlug(),
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit LOGIN_SUCCEEDED userId={}", event.userId().value(), e);
    }
  }

  @ApplicationModuleListener
  void on(LoginFailedEvent event) {
    // Sem tenant resolvido (slug inválido) → log local; sem PK para audit_event.
    if (event.tenantId() == null) {
      log.warn(
          "login falhou sem tenant resolvido identifier={} reason={} ip={}",
          event.usernameAttempted(),
          event.reason(),
          event.ip());
      return;
    }
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("identifier", event.usernameAttempted());
      payload.put("reason", event.reason());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              null,
              null,
              event.occurredAt(),
              "LOGIN_FAILED",
              AuditCategory.AUTH,
              AuditSeverity.INFO,
              "user",
              null,
              event.ip(),
              event.userAgent(),
              null,
              "Tentativa de login falhou em " + event.tenantSlug() + ": " + event.reason(),
              payload));
    } catch (RuntimeException e) {
      log.error(
          "falha gravando audit LOGIN_FAILED tenant={} identifier={}",
          event.tenantSlug(),
          event.usernameAttempted(),
          e);
    }
  }

  @ApplicationModuleListener
  void on(SessionRevokedAllEvent event) {
    AuditSeverity severity =
        "REUSE_DETECTED".equals(event.reason()) ? AuditSeverity.CRITICAL : AuditSeverity.INFO;
    String type =
        "REUSE_DETECTED".equals(event.reason())
            ? "SUSPICIOUS_REFRESH_REUSE"
            : "SESSION_REVOKED_ALL";
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("reason", event.reason());
      payload.put("tokensRevoked", event.tokensRevoked());
      writer.write(
          new AuditEventRecord(
              event.tenantId(),
              event.userId().value(),
              null,
              event.occurredAt(),
              type,
              AuditCategory.AUTH,
              severity,
              "user",
              event.userId().value().toString(),
              event.triggeringIp(),
              null,
              null,
              "Sessões revogadas (" + event.reason() + ") — " + event.tokensRevoked() + " tokens",
              payload));
    } catch (RuntimeException e) {
      log.error("falha gravando audit {} userId={}", type, event.userId().value(), e);
    }
  }
}
