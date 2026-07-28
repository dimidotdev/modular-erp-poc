package dev.dimi.erp.movimentacao;

/**
 * Status de {@code sales_order}. Transições válidas (slice 5.2 vai enforced):
 *
 * <pre>
 * DRAFT → PENDING_APPROVAL → APPROVED → CONVERTED
 *       ↘ CANCELLED          ↘ REJECTED
 *                            ↘ EXPIRED (via cron quando valid_until passa)
 * </pre>
 */
public enum SalesOrderStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  REJECTED,
  CONVERTED,
  CANCELLED,
  EXPIRED
}
