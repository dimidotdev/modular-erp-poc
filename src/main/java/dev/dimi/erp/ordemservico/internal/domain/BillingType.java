package dev.dimi.erp.ordemservico.internal.domain;

/**
 * Tipo de faturamento da OS — interno ao módulo em v1 (slice 9.2b). {@code PARTIAL} cobre
 * adiantamentos/parcelas de OS longa; {@code FINAL} é o faturamento de fechamento que transiciona o
 * status para {@code BILLED}. Promover para a API pública só quando outro módulo precisar
 * discriminar.
 */
public enum BillingType {
  PARTIAL,
  FINAL
}
