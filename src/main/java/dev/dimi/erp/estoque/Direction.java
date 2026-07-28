package dev.dimi.erp.estoque;

/**
 * Sentido de um {@code stock_movement}. Cada constante define semântica de saldo:
 *
 * <ul>
 *   <li>{@link #INBOUND} — entrada (compra, devolução recebida). Recalcula WAC.
 *   <li>{@link #OUTBOUND} — saída efetiva (venda fechada, baixa). Consome reserva quando aplicável.
 *   <li>{@link #TRANSFER_OUT}/{@link #TRANSFER_IN} — par atômico entre filiais com mesmo {@code
 *       origin_id} (RN-EST-008).
 *   <li>{@link #ADJUSTMENT} — correção manual com motivo obrigatório + step-up (RN-EST-010).
 *   <li>{@link #REVERSAL} — estorno de movimento prévio referenciado em {@code reason} via prefixo
 *       {@code REVERSAL_OF:<id>} (RN-EST-009). Não recalcula WAC retroativamente.
 * </ul>
 */
public enum Direction {
  INBOUND,
  OUTBOUND,
  TRANSFER_OUT,
  TRANSFER_IN,
  ADJUSTMENT,
  REVERSAL
}
