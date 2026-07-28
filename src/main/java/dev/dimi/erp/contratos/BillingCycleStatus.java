package dev.dimi.erp.contratos;

/**
 * Estado de um ciclo de faturamento de contrato.
 *
 * <ul>
 *   <li>{@code PENDING}: ciclo criado pelo job; aguardando geração de MVF.
 *   <li>{@code GENERATED}: MVF a receber foi criado em {@code financeiro} com sucesso.
 *   <li>{@code FAILED}: geração falhou (erro técnico ou validação); elegível para regenerate
 *       manual.
 *   <li>{@code SKIPPED}: ciclo intencionalmente ignorado (contrato suspenso no período, etc).
 * </ul>
 *
 * Valores casam com o CHECK constraint da migration de slice 10.2 ({@code billing_cycle.status}).
 */
public enum BillingCycleStatus {
  PENDING,
  GENERATED,
  FAILED,
  SKIPPED
}
