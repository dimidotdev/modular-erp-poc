package dev.dimi.erp.compras;

/**
 * Status de {@code purchase_request} (SC).
 *
 * <pre>
 * DRAFT → PENDING_APPROVAL → APPROVED → QUOTING | ORDERED
 *       ↘ CANCELLED          ↘ REJECTED
 * </pre>
 *
 * RN-COM-001: solicitante com permission {@code compra.solicitar.aprovacao-direta} pula direto pra
 * APPROVED.
 */
public enum PurchaseRequestStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  REJECTED,
  QUOTING,
  ORDERED,
  CANCELLED
}
