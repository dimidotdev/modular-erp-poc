package dev.dimi.erp.auditoria.internal.application;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Comando imutável passado ao {@link AuditWriter}. Mapeia 1:1 com colunas de {@code
 * audit.audit_event} exceto {@code id} (gerado pelo writer) e {@code request_id}/{@code trace_id}
 * (preenchidos a partir do MDC quando disponível).
 *
 * @param tenantId tenant do evento (obrigatório — schema audit é cross-tenant, filtros sempre usam)
 * @param userId actor da operação ({@code null} para system events)
 * @param sessionId sessão JWT ativa quando aplicável
 * @param occurredAt timestamp do evento (deve ser passado pelo publisher, não pelo listener)
 * @param eventType tipo machine-readable (ex: {@code LOGIN_SUCCEEDED}, {@code USER_CREATED})
 * @param category categoria do evento
 * @param severity severidade
 * @param aggregateType tipo do agregado subject ({@code user}, {@code tenant}, {@code invoice})
 * @param aggregateId id do agregado em string (UUID ou composto)
 * @param actorIp IP de origem (inet)
 * @param actorUserAgent User-Agent do request
 * @param correlationId opcional — agrupa eventos da mesma Saga
 * @param summary frase humana em PT-BR, ≤ 500 chars
 * @param payload snapshot estruturado (será serializado para JSONB; {@code null} ou vazio omite)
 */
public record AuditEventRecord(
    TenantId tenantId,
    UUID userId,
    UUID sessionId,
    Instant occurredAt,
    String eventType,
    AuditCategory category,
    AuditSeverity severity,
    String aggregateType,
    String aggregateId,
    String actorIp,
    String actorUserAgent,
    UUID correlationId,
    String summary,
    Map<String, Object> payload) {

  public AuditEventRecord {
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatório");
    if (occurredAt == null) throw new IllegalArgumentException("occurredAt obrigatório");
    if (eventType == null || eventType.isBlank())
      throw new IllegalArgumentException("eventType obrigatório");
    if (category == null) throw new IllegalArgumentException("category obrigatória");
    if (severity == null) throw new IllegalArgumentException("severity obrigatória");
    if (summary == null || summary.isBlank())
      throw new IllegalArgumentException("summary obrigatório");
    if (summary.length() > 500)
      throw new IllegalArgumentException("summary excede 500 chars (RN-AUD-003)");
  }
}
