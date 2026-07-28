package dev.dimi.erp.auditoria.internal.web.dto;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventView;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação REST de uma linha de {@code audit.audit_event}. Espelha {@link AuditEventView}, com
 * {@code payload} mantido como string JSON para o cliente desserializar conforme o tipo do evento.
 */
public record AuditEventApiResponse(
    UUID id,
    UUID tenantId,
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
    UUID requestId,
    String traceId,
    UUID correlationId,
    String summary,
    String payload) {

  public static AuditEventApiResponse from(AuditEventView v) {
    return from(v, true);
  }

  /** Redige metadados de origem e payload quando o caller não possui permissão sensível. */
  public static AuditEventApiResponse from(AuditEventView v, boolean includeSensitive) {
    return new AuditEventApiResponse(
        v.id(),
        v.tenantId(),
        v.userId(),
        v.sessionId(),
        v.occurredAt(),
        v.eventType(),
        v.category(),
        v.severity(),
        v.aggregateType(),
        v.aggregateId(),
        includeSensitive ? v.actorIp() : null,
        includeSensitive ? v.actorUserAgent() : null,
        v.requestId(),
        v.traceId(),
        v.correlationId(),
        v.summary(),
        includeSensitive ? v.payload() : null);
  }
}
