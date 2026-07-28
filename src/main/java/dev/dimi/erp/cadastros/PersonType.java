package dev.dimi.erp.cadastros;

/**
 * Tipo polimórfico da pessoa. Espelha o CHECK constraint em {@code person.type}. Imutável após
 * criação (RN-CAD-003).
 */
public enum PersonType {
  /** Pessoa Física. */
  PF,
  /** Pessoa Jurídica. */
  PJ
}
