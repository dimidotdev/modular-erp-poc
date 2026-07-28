package dev.dimi.erp.auditoria.internal.application;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import java.time.Instant;
import java.util.UUID;

/**
 * Projeção read-only de uma linha de {@code audit.audit_event}. Não é entidade JPA — a tabela é
 * particionada por mês e modelar como {@code @Entity} forçaria {@code @IdClass} ou
 * {@code @EmbeddedId} sem ganho funcional (RN-AUD-002: append-only, sem dirty-checking). Consumida
 * pelos endpoints de consulta administrativa (slice futuro de UI auditoria).
 *
 * <p>{@code payload} é o JSON serializado como string — o caller decide se desserializa para Map ou
 * passa adiante.
 */
public record AuditEventView(
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
    String payload) {}
