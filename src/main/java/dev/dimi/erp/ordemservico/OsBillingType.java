package dev.dimi.erp.ordemservico;

/**
 * Tipo de faturamento de OS exposto na API pública (REST + integração entre módulos). Espelha o
 * enum interno {@code dev.dimi.erp.ordemservico.internal.domain.BillingType} mas vive aqui na raiz
 * pública para poder ser usado por DTOs do controller sem violar encapsulamento do {@code
 * internal/}.
 *
 * <p>{@code PARTIAL} cobre adiantamentos/parcelas em OS longa. {@code FINAL} é o faturamento de
 * fechamento — transiciona a OS para {@code BILLED}.
 */
public enum OsBillingType {
  PARTIAL,
  FINAL
}
