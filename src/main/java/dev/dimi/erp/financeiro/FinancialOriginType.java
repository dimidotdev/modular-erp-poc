package dev.dimi.erp.financeiro;

/**
 * Origem da MVF. SALE/PURCHASE/OS/CONTRACT referenciam {@code origin_id} ao agregado
 * correspondente; MANUAL deixa {@code origin_id} nulo.
 */
public enum FinancialOriginType {
  SALE,
  PURCHASE,
  OS,
  CONTRACT,
  MANUAL,
}
