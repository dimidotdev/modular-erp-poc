package dev.dimi.erp.fiscal;

/**
 * Ciclo de vida do documento fiscal.
 *
 * <pre>
 * DRAFT → PENDING → AUTHORIZED → CANCELLED
 *               ↘ REJECTED → DRAFT (retry)
 *                        ↘ INUTILIZED (faixa de número descartada).
 * </pre>
 *
 * CONTINGENCY entra no slice 8.6 (RN-FIS-013).
 */
public enum InvoiceStatus {
  DRAFT,
  PENDING,
  AUTHORIZED,
  REJECTED,
  CANCELLED,
  INUTILIZED
}
