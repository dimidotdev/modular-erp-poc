package dev.dimi.erp.movimentacao;

/**
 * Status de {@code sale_return}. Devolução fechada (CLOSED) é o momento em que estoque é
 * atualizado, MVF estornado e NFe de devolução emitida (RN-MOV-013..018).
 */
public enum ReturnStatus {
  OPEN,
  CLOSED,
  CANCELLED
}
