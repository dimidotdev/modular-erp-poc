package dev.dimi.erp.cadastros;

/**
 * Papéis comerciais que uma pessoa pode desempenhar. Espelha o CHECK constraint em {@code
 * person_modality.modality}. Múltiplas modalidades coexistem na mesma pessoa (RN-CAD-005).
 */
public enum Modality {
  CUSTOMER,
  SUPPLIER,
  CARRIER,
  SELLER,
  EMPLOYEE
}
