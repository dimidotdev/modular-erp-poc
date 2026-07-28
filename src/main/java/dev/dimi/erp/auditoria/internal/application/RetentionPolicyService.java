package dev.dimi.erp.auditoria.internal.application;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.RetentionAction;
import dev.dimi.erp.auditoria.internal.domain.RetentionPolicy;
import dev.dimi.erp.auditoria.internal.domain.RetentionRun;
import dev.dimi.erp.auditoria.internal.persistence.RetentionPolicyRepository;
import dev.dimi.erp.auditoria.internal.persistence.RetentionRunRepository;
import dev.dimi.erp.identidade.UserId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gerência administrativa de políticas de retenção e da trilha histórica de execuções. Endpoints
 * REST do slice 11 (UI de auditoria) delegam aqui.
 *
 * <ul>
 *   <li>{@link #listPolicies()} — lista todas as 10 políticas seedadas (uma por {@link
 *       AuditCategory}).
 *   <li>{@link #updatePolicy(AuditCategory, int, RetentionAction, UserId)} — ajusta {@code
 *       retainDays} e/ou {@code action_at_eol} de uma política existente. {@code actor} é repassado
 *       para meta-auditoria futura (mudanças de política são por si só auditáveis na categoria
 *       {@code ADMIN}); em V1 apenas logamos a operação.
 *   <li>{@link #listRecentRuns(int)} — top N execuções para dashboard administrativo.
 * </ul>
 *
 * <p>{@code @Transactional} é granular por método para minimizar lock — listagens são readOnly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionPolicyService {

  private final RetentionPolicyRepository policies;
  private final RetentionRunRepository runs;

  @Transactional(readOnly = true)
  public List<RetentionPolicy> listPolicies() {
    return policies.findAll();
  }

  /**
   * Atualiza a política da categoria informada. Lança {@link IllegalArgumentException} se a
   * política não existir (não há criação dinâmica — políticas são seedadas via Flyway V9).
   *
   * @param category categoria alvo
   * @param retainDays nova janela em dias (validado em {@link RetentionPolicy#update})
   * @param action nova ação de fim-de-vida
   * @param actor usuário responsável — repassado para meta-auditoria (v1: apenas log)
   * @return a {@link RetentionPolicy} já atualizada
   */
  @Transactional
  public RetentionPolicy updatePolicy(
      AuditCategory category, int retainDays, RetentionAction action, UserId actor) {
    Objects.requireNonNull(category, "category obrigatória");
    RetentionPolicy policy =
        policies
            .findById(category)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "RetentionPolicy não encontrada para categoria " + category));
    policy.update(retainDays, action);
    // V1: meta-auditoria (RetentionPolicyChangedEvent → AdminAuditListener) entra junto com o
    // endpoint REST do slice 11. Aqui apenas logamos com actor identificável.
    log.info(
        "RetentionPolicy updated category={} retainDays={} action={} actor={}",
        category,
        retainDays,
        action,
        actor == null ? null : actor.value());
    return policy;
  }

  /**
   * Top N execuções recentes ordenadas por {@code started_at DESC}. Pageable é construído aqui para
   * o caller passar apenas {@code limit}.
   */
  @Transactional(readOnly = true)
  public List<RetentionRun> listRecentRuns(int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit deve ser positivo");
    }
    // findTop10ByOrderByStartedAtDesc já existe no repository para o caminho de dashboard padrão;
    // para limites arbitrários usamos findAll com Pageable.
    if (limit == 10) {
      return runs.findTop10ByOrderByStartedAtDesc();
    }
    return runs.findAll(
            PageRequest.of(
                0,
                limit,
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "startedAt")))
        .getContent();
  }
}
