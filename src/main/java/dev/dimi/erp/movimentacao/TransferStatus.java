package dev.dimi.erp.movimentacao;

/**
 * Status de {@code stock_transfer}. CLOSED dispara o par atômico TRANSFER_OUT/TRANSFER_IN via
 * {@code StockInventoryPort.transfer} + NFe quando entre CNPJs distintos (RN-MOV-019/020).
 */
public enum TransferStatus {
  OPEN,
  CLOSED,
  CANCELLED
}
