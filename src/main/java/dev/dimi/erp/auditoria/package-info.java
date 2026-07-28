/**
 * Módulo Auditoria.
 *
 * <p>Bounded context conforme docs/domain/19-auditoria.md. Subpacote {@code internal/} contém a
 * implementação privada; outros módulos só podem importar tipos da raiz deste pacote (API pública:
 * eventos, IDs tipados, ports/services expostos).
 *
 * <p>Slice 11 — módulo <strong>consumer</strong> de eventos de todos os bounded contexts do v1:
 * identidade, cadastros, fiscal, financeiro, estoque, movimentacao, compras, ordemservico,
 * contratos. Para cada evento de domínio relevante, um {@code @ApplicationModuleListener} traduz
 * para uma linha em {@code audit.audit_event} (append-only, particionado por mês via {@code
 * RANGE(occurred_at)}, ver {@code V4__create_audit_event.sql}).
 *
 * <p>Retenção configurável por categoria em {@code audit.retention_policy} (V9), com job periódico
 * (ShedLock {@code platform.shedlock}) que purga ou anonimiza linhas além do {@code retain_days} e
 * mantém janela rolante de partições. Trilha histórica em {@code audit.retention_run}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Auditoria",
    allowedDependencies = {
      "shared",
      "identidade",
      "cadastros",
      "produto",
      "fiscal",
      "financeiro",
      "estoque",
      "movimentacao",
      "compras",
      "ordemservico",
      "contratos"
    })
package dev.dimi.erp.auditoria;
