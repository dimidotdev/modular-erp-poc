package dev.dimi.erp.fiscal;

/**
 * Origem do documento fiscal. SALE/PURCHASE/OS/CONTRACT referenciam {@code origin_id} ao agregado
 * correspondente; MANUAL deixa {@code origin_id} nulo. Espelha {@code
 * financeiro.FinancialOriginType}.
 */
public enum FiscalOriginType {
  SALE,
  PURCHASE,
  OS,
  MANUAL,
  CONTRACT
}
