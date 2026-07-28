package dev.dimi.erp.compras;

/**
 * Status de {@code quotation}.
 *
 * <pre>
 * OPEN → SENT → RECEIVING → CLOSED
 *      ↘ CANCELLED
 * </pre>
 */
public enum QuotationStatus {
  OPEN,
  SENT,
  RECEIVING,
  CLOSED,
  CANCELLED
}
