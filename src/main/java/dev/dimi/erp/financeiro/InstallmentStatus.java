package dev.dimi.erp.financeiro;

/** Status de {@code financial_installment} — espelha o do record, mas por parcela. */
public enum InstallmentStatus {
  OPEN,
  PARTIALLY_PAID,
  PAID,
  CANCELLED,
  RENEGOTIATED,
}
