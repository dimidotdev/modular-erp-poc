package dev.dimi.erp.auditoria.internal.persistence;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventView;
import dev.dimi.erp.auditoria.internal.application.AuditQueryCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Consulta read-only de {@code audit.audit_event}.
 *
 * <p>Usa SQL nativo (não Spring Data JPA) porque a tabela é particionada por mês e usa o schema
 * {@code audit} fora do {@code search_path} multi-tenant — SQL totalmente qualificado é mais
 * previsível e permite construir filtros dinâmicos sem fragilidade de Criteria API ou JPQL com
 * tabela schema-cross.
 *
 * <p>Filtros obrigatórios:
 *
 * <ul>
 *   <li>{@code tenant_id} sempre filtrado salvo quando o caller é platform admin (RN-AUD-005).
 * </ul>
 *
 * <p>Ordenação fixa: {@code ORDER BY occurred_at DESC, id DESC} (deterministic tiebreak em rows do
 * mesmo timestamp).
 */
@Repository
public class AuditEventQueryRepository {

  private static final String BASE_SELECT =
      """
      SELECT id, tenant_id, user_id, session_id, occurred_at,
             event_type, event_category, severity,
             aggregate_type, aggregate_id,
             host(actor_ip) AS actor_ip, actor_user_agent,
             request_id, trace_id, correlation_id,
             summary, payload::text AS payload
      FROM audit.audit_event
      """;

  @PersistenceContext private EntityManager em;

  /**
   * Busca paginada com filtros dinâmicos. Sempre aplica {@code tenant_id = :tenantId} exceto quando
   * {@link AuditQueryCriteria#isPlatformAdmin()} é true.
   */
  public Page<AuditEventView> search(AuditQueryCriteria criteria, Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object[]> params = new ArrayList<>(); // [name, value]

    if (!criteria.isPlatformAdmin()) {
      where.append(" AND tenant_id = :tenantId");
      params.add(new Object[] {"tenantId", criteria.tenantId()});
    } else if (criteria.tenantId() != null) {
      // platform admin pode opcionalmente filtrar por tenant
      where.append(" AND tenant_id = :tenantId");
      params.add(new Object[] {"tenantId", criteria.tenantId()});
    }

    if (!criteria.includeSensitive()) {
      where.append(" AND severity <> 'CRITICAL'");
    }

    if (criteria.category() != null) {
      where.append(" AND event_category = :category");
      params.add(new Object[] {"category", criteria.category().name()});
    }
    if (criteria.eventType() != null && !criteria.eventType().isBlank()) {
      where.append(" AND event_type = :eventType");
      params.add(new Object[] {"eventType", criteria.eventType()});
    }
    if (criteria.aggregateType() != null && !criteria.aggregateType().isBlank()) {
      where.append(" AND aggregate_type = :aggregateType");
      params.add(new Object[] {"aggregateType", criteria.aggregateType()});
    }
    if (criteria.aggregateId() != null && !criteria.aggregateId().isBlank()) {
      where.append(" AND aggregate_id = :aggregateId");
      params.add(new Object[] {"aggregateId", criteria.aggregateId()});
    }
    if (criteria.userId() != null) {
      where.append(" AND user_id = :userId");
      params.add(new Object[] {"userId", criteria.userId()});
    }
    if (criteria.from() != null) {
      where.append(" AND occurred_at >= :from");
      params.add(new Object[] {"from", Timestamp.from(criteria.from())});
    }
    if (criteria.to() != null) {
      where.append(" AND occurred_at < :to");
      params.add(new Object[] {"to", Timestamp.from(criteria.to())});
    }
    if (criteria.severity() != null) {
      where.append(" AND severity = :severity");
      params.add(new Object[] {"severity", criteria.severity().name()});
    }
    if (criteria.q() != null && !criteria.q().isBlank()) {
      where.append(" AND summary ILIKE :q");
      params.add(new Object[] {"q", "%" + criteria.q() + "%"});
    }

    // Count
    String countSql = "SELECT count(*) FROM audit.audit_event" + where;
    Query countQ = em.createNativeQuery(countSql);
    bind(countQ, params);
    long total = ((Number) countQ.getSingleResult()).longValue();

    if (total == 0L) {
      return new PageImpl<>(List.of(), pageable, 0L);
    }

    // Page
    String pageSql =
        BASE_SELECT
            + where
            + " ORDER BY occurred_at DESC, id DESC"
            + " LIMIT :limit OFFSET :offset";
    Query pageQ = em.createNativeQuery(pageSql);
    bind(pageQ, params);
    pageQ.setParameter("limit", pageable.getPageSize());
    pageQ.setParameter("offset", pageable.getOffset());

    @SuppressWarnings("unchecked")
    List<Object[]> rows = pageQ.getResultList();
    List<AuditEventView> content = rows.stream().map(AuditEventQueryRepository::map).toList();
    return new PageImpl<>(content, pageable, total);
  }

  public Optional<AuditEventView> findById(UUID id, UUID tenantId, boolean includeSensitive) {
    String sensitiveFilter = includeSensitive ? "" : " AND severity <> 'CRITICAL'";
    Query q =
        em.createNativeQuery(
            BASE_SELECT + " WHERE id = :id AND tenant_id = :tenantId" + sensitiveFilter);
    q.setParameter("id", id);
    q.setParameter("tenantId", tenantId);
    @SuppressWarnings("unchecked")
    List<Object[]> rows = q.getResultList();
    if (rows.isEmpty()) return Optional.empty();
    return Optional.of(map(rows.get(0)));
  }

  public List<AuditEventView> findByAggregate(
      UUID tenantId, String aggregateType, String aggregateId, boolean includeSensitive) {
    String sensitiveFilter = includeSensitive ? "" : " AND severity <> 'CRITICAL'";
    Query q =
        em.createNativeQuery(
            BASE_SELECT
                + " WHERE tenant_id = :tenantId"
                + " AND aggregate_type = :aggregateType"
                + " AND aggregate_id = :aggregateId"
                + sensitiveFilter
                + " ORDER BY occurred_at DESC, id DESC");
    q.setParameter("tenantId", tenantId);
    q.setParameter("aggregateType", aggregateType);
    q.setParameter("aggregateId", aggregateId);
    @SuppressWarnings("unchecked")
    List<Object[]> rows = q.getResultList();
    return rows.stream().map(AuditEventQueryRepository::map).toList();
  }

  public List<AuditEventView> findByCorrelation(UUID correlationId) {
    Query q =
        em.createNativeQuery(
            BASE_SELECT
                + " WHERE correlation_id = :correlationId"
                + " ORDER BY occurred_at ASC, id ASC");
    q.setParameter("correlationId", correlationId);
    @SuppressWarnings("unchecked")
    List<Object[]> rows = q.getResultList();
    return rows.stream().map(AuditEventQueryRepository::map).toList();
  }

  private static void bind(Query q, List<Object[]> params) {
    for (Object[] p : params) {
      q.setParameter((String) p[0], p[1]);
    }
  }

  private static AuditEventView map(Object[] r) {
    int i = 0;
    UUID id = (UUID) r[i++];
    UUID tenantId = (UUID) r[i++];
    UUID userId = (UUID) r[i++];
    UUID sessionId = (UUID) r[i++];
    Instant occurredAt = ((Timestamp) r[i++]).toInstant();
    String eventType = (String) r[i++];
    AuditCategory category = AuditCategory.valueOf((String) r[i++]);
    AuditSeverity severity = AuditSeverity.valueOf((String) r[i++]);
    String aggregateType = (String) r[i++];
    String aggregateId = (String) r[i++];
    String actorIp = (String) r[i++];
    String actorUserAgent = (String) r[i++];
    UUID requestId = (UUID) r[i++];
    String traceId = (String) r[i++];
    UUID correlationId = (UUID) r[i++];
    String summary = (String) r[i++];
    String payload = (String) r[i];
    return new AuditEventView(
        id,
        tenantId,
        userId,
        sessionId,
        occurredAt,
        eventType,
        category,
        severity,
        aggregateType,
        aggregateId,
        actorIp,
        actorUserAgent,
        requestId,
        traceId,
        correlationId,
        summary,
        payload);
  }
}
