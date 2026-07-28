package dev.dimi.erp.contratos;

/**
 * Periodicidade de faturamento do contrato. Define o intervalo entre {@code billing_cycle}s gerados
 * pelo job de slice 10.2.
 *
 * <p>Valores casam com o CHECK constraint da migration V23 ({@code contract.recurrence}).
 */
public enum Recurrence {
  MONTHLY,
  BIWEEKLY,
  QUARTERLY,
  SEMIANNUAL,
  ANNUAL
}
