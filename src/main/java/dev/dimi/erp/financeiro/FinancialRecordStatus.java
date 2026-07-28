package dev.dimi.erp.financeiro;

/**
 * Status do {@code financial_record}.
 *
 * <pre>
 * OPEN → PARTIALLY_PAID → PAID
 *      ↘ CANCELLED
 *      ↘ RENEGOTIATED (gera nova MVF substituta; original arquiva).
 * </pre>
 */
public enum FinancialRecordStatus {
  OPEN,
  PARTIALLY_PAID,
  PAID,
  CANCELLED,
  RENEGOTIATED,
}
