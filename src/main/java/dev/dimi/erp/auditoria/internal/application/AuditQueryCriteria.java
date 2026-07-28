package dev.dimi.erp.auditoria.internal.application;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import java.time.Instant;
import java.util.UUID;

/**
 * Critérios de filtro para {@link AuditEventQueryRepository#search}. Todos os campos exceto {@link
 * #tenantId} e {@link #isPlatformAdmin} são opcionais (nulos = filtro não aplicado).
 *
 * <p>Regra de visibilidade:
 *
 * <ul>
 *   <li>Usuários de tenant: {@code tenantId} obrigatório, {@code isPlatformAdmin=false}.
 *   <li>Platform admins: podem omitir {@code tenantId} (consulta cross-tenant) passando {@code
 *       isPlatformAdmin=true}.
 * </ul>
 *
 * @param tenantId tenant alvo — obrigatório exceto quando {@code isPlatformAdmin=true}
 * @param isPlatformAdmin caller é platform admin com permissão {@code platform.audit.view}
 * @param category filtra por {@link AuditCategory}
 * @param eventType match exato no {@code event_type}
 * @param aggregateType match exato no {@code aggregate_type}
 * @param aggregateId match exato no {@code aggregate_id}
 * @param userId actor do evento
 * @param from limite inferior (inclusivo) em {@code occurred_at}
 * @param to limite superior (exclusivo) em {@code occurred_at}
 * @param severity filtra por severidade
 * @param q full-text simples em {@code summary} ({@code ILIKE '%q%'})
 * @param includeSensitive inclui eventos CRITICAL; exige {@code audit.sensitive.consultar}
 */
public record AuditQueryCriteria(
    UUID tenantId,
    boolean isPlatformAdmin,
    AuditCategory category,
    String eventType,
    String aggregateType,
    String aggregateId,
    UUID userId,
    Instant from,
    Instant to,
    AuditSeverity severity,
    String q,
    boolean includeSensitive) {

  public AuditQueryCriteria {
    if (tenantId == null && !isPlatformAdmin) {
      throw new IllegalArgumentException(
          "tenantId obrigatório exceto para platform admin (cross-tenant)");
    }
  }
}
