package dev.dimi.erp.cadastros;

/**
 * Regime tributário da filial. Espelha o CHECK constraint em {@code branch_fiscal.tax_regime}.
 * Determina quais campos fiscais (CSOSN para Simples; CST ICMS para Presumido/Real) são exigidos
 * pelo módulo fiscal na emissão de documentos.
 */
public enum TaxRegime {
  SIMPLES,
  SIMPLES_EXCEDENTE,
  PRESUMIDO,
  REAL,
  MEI
}
