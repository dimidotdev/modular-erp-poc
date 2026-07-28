package dev.dimi.erp.compras;

/**
 * Status de {@code purchase_order} (PC). PARTIALLY_RECEIVED = pelo menos 1 receipt parcial.
 * RECEIVED = todos itens recebidos completamente. RN-COM-010: cancelamento em PARTIALLY_RECEIVED só
 * dos itens ainda não recebidos.
 */
public enum PurchaseOrderStatus {
  OPEN,
  SENT,
  PARTIALLY_RECEIVED,
  RECEIVED,
  CANCELLED
}
