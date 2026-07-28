package dev.dimi.erp.auditoria.internal.application;

import dev.dimi.erp.auditoria.internal.persistence.AuditEventQueryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade read-only para consulta de {@code audit.audit_event}. Delega ao {@link
 * AuditEventQueryRepository} e expõe os quatro modos de leitura usados pelos endpoints
 * administrativos do slice 11:
 *
 * <ul>
 *   <li>{@link #search(AuditQueryCriteria, Pageable)} — busca paginada com filtros dinâmicos.
 *   <li>{@link #findById(UUID, UUID, boolean)} — lookup tenant-scoped de um evento específico.
 *   <li>{@link #findByAggregate(UUID, String, String, boolean)} — trilha completa de um agregado
 *       dentro de um tenant.
 *   <li>{@link #findByCorrelation(UUID)} — todos os eventos de uma Saga (correlation_id é
 *       cross-tenant em design).
 * </ul>
 *
 * <p>Autorização (filtro por tenant e permissions {@code audit.consultar} / {@code
 * audit.sensitive.consultar}) é responsabilidade do controller — este service confia em {@link
 * AuditQueryCriteria} já construído com as flags corretas.
 *
 * <p>{@code @Transactional(readOnly = true)} permite hint de leitura ao driver e desliga
 * dirty-checking — o repository usa SQL nativo cross-schema ({@code audit.*} fora do search_path
 * multi-tenant) então não há entidades JPA na sessão de qualquer forma.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditQueryService {

  private final AuditEventQueryRepository repository;

  /** Busca paginada com filtros dinâmicos. Vide {@link AuditQueryCriteria} para regras. */
  public Page<AuditEventView> search(AuditQueryCriteria criteria, Pageable pageable) {
    return repository.search(criteria, pageable);
  }

  /** Lookup por id restrito ao tenant e à visibilidade sensível do caller. */
  public Optional<AuditEventView> findById(UUID id, UUID tenantId, boolean includeSensitive) {
    return repository.findById(id, tenantId, includeSensitive);
  }

  /** Trilha completa do agregado dentro de um tenant, ordenada {@code occurred_at DESC}. */
  public List<AuditEventView> findByAggregate(
      UUID tenantId, String aggregateType, String aggregateId, boolean includeSensitive) {
    return repository.findByAggregate(tenantId, aggregateType, aggregateId, includeSensitive);
  }

  /**
   * Todos os eventos de uma mesma Saga, ordenados {@code occurred_at ASC}. {@code correlation_id} é
   * cross-tenant por design (cancelamento que cruza módulos pode envolver eventos system) então o
   * controller decide se o caller tem permissão.
   */
  public List<AuditEventView> findByCorrelation(UUID correlationId) {
    return repository.findByCorrelation(correlationId);
  }
}
