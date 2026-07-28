package dev.dimi.erp.movimentacao;

/**
 * Status de {@code sale}. Sale fechada é imutável (RN-MOV-008) — modificações exigem cancelamento +
 * nova venda.
 *
 * <p>{@link #CANCELLATION_IN_PROGRESS} é estado transiente da Saga de cancelamento (RN-MOV-010);
 * {@link #PARTIAL_CANCELLATION_FAILED} indica que algum step da Saga falhou terminalmente.
 *
 * <p>{@link #LOCKED_FOR_CANCELLATION} sinaliza que a janela configurada para cancelamento expirou
 * (RN-MOV-012, default 30 dias). Cancelamento bloqueado nesse estado.
 */
public enum SaleStatus {
  OPEN,
  CLOSED,
  CANCELLATION_IN_PROGRESS,
  CANCELLED,
  PARTIAL_CANCELLATION_FAILED,
  LOCKED_FOR_CANCELLATION
}
