package dev.dimi.erp.auditoria.internal.web;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditEventNotFoundException;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditQueryCriteria;
import dev.dimi.erp.auditoria.internal.application.AuditQueryService;
import dev.dimi.erp.auditoria.internal.web.dto.AuditEventApiResponse;
import dev.dimi.erp.identidade.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de consulta de {@code audit.audit_event} ({@code /api/v1/auditoria/events/**}).
 *
 * <p>Camada web somente leitura. Tenant é sempre extraído do {@link AuthenticatedUser} (nunca de
 * path/query/body), respeitando RN-AUD-005.
 *
 * <p>Cortes deliberados de v1:
 *
 * <ul>
 *   <li>{@code /events/export} (CSV/Parquet) não faz parte do POC.
 *   <li>{@code /sensitive-events} e {@code /lgpd-events}: cliente reusa {@code /events} com {@code
 *       category} fixa — sem endpoint dedicado.
 *   <li>{@code /platform/events} (cross-tenant para PLATFORM_ADMIN) exige uma política dedicada de
 *       administração da plataforma.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
class AuditController {

  private static final int MAX_PAGE_SIZE = 100;
  private static final String SENSITIVE_PERMISSION = "audit.sensitive.consultar";

  private final AuditQueryService queryService;

  /**
   * Busca paginada com filtros dinâmicos. Sempre escopo de tenant — {@code isPlatformAdmin=false}
   * fixo (cross-tenant fica para o endpoint dedicado de platform admin em v1.1+).
   */
  @GetMapping("/events")
  @PreAuthorize("hasAuthority('audit.consultar')")
  public Page<AuditEventApiResponse> list(
      @RequestParam(required = false) AuditCategory category,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String aggregateType,
      @RequestParam(required = false) String aggregateId,
      @RequestParam(required = false) UUID userId,
      @RequestParam(required = false) AuditSeverity severity,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    boolean includeSensitive = principal.hasPermission(SENSITIVE_PERMISSION);
    Pageable pageable =
        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
    var criteria =
        new AuditQueryCriteria(
            principal.tenantId().value(),
            false,
            category,
            type,
            aggregateType,
            aggregateId,
            userId,
            from,
            to,
            severity,
            q,
            includeSensitive);
    return queryService
        .search(criteria, pageable)
        .map(event -> AuditEventApiResponse.from(event, includeSensitive));
  }

  /**
   * Lookup direto. Se o evento existir em outro tenant, devolvemos o mesmo 404 para não vazar
   * existência cross-tenant.
   */
  @GetMapping("/events/{id}")
  @PreAuthorize("hasAuthority('audit.consultar')")
  public AuditEventApiResponse byId(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    boolean includeSensitive = principal.hasPermission(SENSITIVE_PERMISSION);
    var event =
        queryService
            .findById(id, principal.tenantId().value(), includeSensitive)
            .orElseThrow(() -> new AuditEventNotFoundException(id));
    return AuditEventApiResponse.from(event, includeSensitive);
  }

  /** Trilha completa de um agregado dentro do tenant atual, ordenada {@code occurred_at DESC}. */
  @GetMapping("/events/by-aggregate/{aggregateType}/{aggregateId}")
  @PreAuthorize("hasAuthority('audit.consultar')")
  public List<AuditEventApiResponse> byAggregate(
      @PathVariable String aggregateType,
      @PathVariable String aggregateId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    boolean includeSensitive = principal.hasPermission(SENSITIVE_PERMISSION);
    return queryService
        .findByAggregate(principal.tenantId().value(), aggregateType, aggregateId, includeSensitive)
        .stream()
        .map(event -> AuditEventApiResponse.from(event, includeSensitive))
        .toList();
  }
}
