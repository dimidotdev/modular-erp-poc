package dev.dimi.erp.auditoria.internal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.uuid.UuidCreator;
import java.sql.Types;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Único componente que escreve em {@code audit.audit_event} (RN-AUD-002).
 *
 * <p>Usa {@link JdbcTemplate} direto em vez de JPA pelos seguintes motivos:
 *
 * <ul>
 *   <li>Tabela é append-only — dirty checking / cascade do JPA não traz valor.
 *   <li>PK composta {@code (id, occurred_at)} é particionamento, não chave de domínio — modelar via
 *       {@code @IdClass}/{@code @EmbeddedId} adicionaria boilerplate sem ganho.
 *   <li>{@code payload} JSONB resolvido com cast {@code ?::jsonb} no SQL — sem precisar de tipos
 *       JDBC vendor-específicos.
 *   <li>Schema {@code audit} é cross-tenant — fora do search_path multi-tenant; SQL totalmente
 *       qualificado é mais previsível.
 * </ul>
 *
 * <p>Falhas de escrita lançam exceção; o caller (listener) decide se loga e segue ou propaga. Em
 * regra geral, listeners de domínio devem capturar e logar para não bloquear o fluxo de negócio
 * (RN-AUD-002).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditWriter {

  private static final String INSERT_SQL =
      """
      INSERT INTO audit.audit_event (
        id, tenant_id, user_id, session_id, occurred_at,
        event_type, event_category, severity,
        aggregate_type, aggregate_id,
        actor_ip, actor_user_agent, request_id, trace_id, correlation_id,
        summary, payload
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?::jsonb)
      """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public void write(AuditEventRecord event) {
    UUID id = UuidCreator.getTimeOrderedEpoch();
    UUID requestId = parseUuidFromMdc("requestId");
    String traceId = MDC.get("traceId");
    String payloadJson = serializePayload(event);

    jdbc.update(
        INSERT_SQL,
        ps -> {
          int i = 1;
          ps.setObject(i++, id);
          ps.setObject(i++, event.tenantId().value());
          ps.setObject(i++, event.userId(), Types.OTHER);
          ps.setObject(i++, event.sessionId(), Types.OTHER);
          ps.setObject(i++, java.sql.Timestamp.from(event.occurredAt()));
          ps.setString(i++, event.eventType());
          ps.setString(i++, event.category().name());
          ps.setString(i++, event.severity().name());
          ps.setString(i++, event.aggregateType());
          ps.setString(i++, event.aggregateId());
          ps.setString(i++, event.actorIp());
          ps.setString(i++, event.actorUserAgent());
          ps.setObject(i++, requestId, Types.OTHER);
          ps.setString(i++, traceId);
          ps.setObject(i++, event.correlationId(), Types.OTHER);
          ps.setString(i++, event.summary());
          ps.setString(i, payloadJson);
        });

    log.debug(
        "audit gravado type={} tenantId={} userId={}",
        event.eventType(),
        event.tenantId().value(),
        event.userId());
  }

  private String serializePayload(AuditEventRecord event) {
    if (event.payload() == null || event.payload().isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(AuditPayloadSanitizer.sanitize(event.payload()));
    } catch (JsonProcessingException e) {
      // Não queremos derrubar o listener por payload mal-formado — grava sem payload e loga.
      log.warn(
          "falha serializando payload do audit event type={} — gravando sem payload",
          event.eventType(),
          e);
      return null;
    }
  }

  private static UUID parseUuidFromMdc(String key) {
    String raw = MDC.get(key);
    if (raw == null || raw.isBlank()) return null;
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
