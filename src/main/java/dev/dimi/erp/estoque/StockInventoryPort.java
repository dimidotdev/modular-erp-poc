package dev.dimi.erp.estoque;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port consumido por outros módulos (compras, movimentação, ordem-servico) para operar o saldo de
 * estoque sem importar internals. Toda chamada precisa rodar dentro de transação (caller decide
 * propagação) e usa lock pessimista no snapshot afetado (RN-EST-004) — concorrência é segura por
 * design.
 *
 * <p>Cada operação aceita {@code idempotencyKey} no formato sugerido {@code
 * <origin_type>:<origin_id>:<sub_seq>}. Tentativa duplicada retorna o {@link MovementId} existente
 * sem efeito colateral (RN-EST-005).
 */
public interface StockInventoryPort {

  /**
   * Reserva saldo virtual — {@code quantity_reserved} sobe, {@code quantity_on_hand} permanece.
   * RN-EST-007. Não gera movimento no ledger (reserva é "número virtual").
   */
  void reserve(ReserveCommand cmd);

  /** Libera reserva prévia sem efetivar saída. */
  void releaseReservation(ReleaseReservationCommand cmd);

  /**
   * Efetiva uma reserva: consome reserva + saldo on-hand E grava movimento OUTBOUND no ledger.
   * Usado quando venda em aberto fecha.
   */
  MovementId ship(ShipCommand cmd);

  /**
   * Entrada com custo. Recalcula WAC (RN-EST-001). Grava movimento INBOUND. Usado pelo recebimento
   * de compra.
   */
  MovementId receive(ReceiveCommand cmd);

  /**
   * Transferência entre filiais — par atômico TRANSFER_OUT + TRANSFER_IN com mesmo {@code
   * transferId} como {@code origin_id}. Rollback total em qualquer falha (RN-EST-008).
   */
  TransferResult transfer(TransferCommand cmd);

  /**
   * Ajuste manual (RN-EST-010). Sensitive — caller (controller) deve aplicar step-up. Delta
   * positivo = entrada, negativo = saída. Grava movimento ADJUSTMENT.
   */
  MovementId adjust(AdjustCommand cmd);

  /**
   * Estorno de movimento prévio (RN-EST-009). Cria movimento REVERSAL com sentido inverso ao
   * original, apontando para ele em {@code reason}. NÃO recalcula WAC retroativamente.
   */
  MovementId reverse(ReverseCommand cmd);

  // --------------------------------------------------------------------------
  // Commands
  // --------------------------------------------------------------------------

  record ReserveCommand(
      PackagingId packagingId,
      BranchId branchId,
      StockLocationId locationId,
      StockBatchId batchId,
      BigDecimal quantity,
      String originType,
      UUID originId,
      String idempotencyKey,
      UserId actor) {}

  record ReleaseReservationCommand(
      PackagingId packagingId,
      BranchId branchId,
      StockLocationId locationId,
      StockBatchId batchId,
      BigDecimal quantity,
      String originType,
      UUID originId,
      UserId actor) {}

  record ShipCommand(
      PackagingId packagingId,
      BranchId branchId,
      StockLocationId locationId,
      StockBatchId batchId,
      BigDecimal quantity,
      String originType,
      UUID originId,
      String idempotencyKey,
      UserId actor) {}

  record ReceiveCommand(
      PackagingId packagingId,
      BranchId branchId,
      StockLocationId locationId,
      StockBatchId batchId,
      BigDecimal quantity,
      BigDecimal unitCost,
      String originType,
      UUID originId,
      String idempotencyKey,
      UserId actor) {}

  record TransferCommand(
      PackagingId packagingId,
      BranchId sourceBranchId,
      StockLocationId sourceLocationId,
      BranchId destBranchId,
      StockLocationId destLocationId,
      StockBatchId batchId,
      BigDecimal quantity,
      UUID transferId,
      String reason,
      UserId actor) {}

  record AdjustCommand(
      PackagingId packagingId,
      BranchId branchId,
      StockLocationId locationId,
      StockBatchId batchId,
      BigDecimal delta,
      String reason,
      UserId actor) {}

  record ReverseCommand(MovementId movementId, String reason, UserId actor) {}

  record TransferResult(MovementId outMovementId, MovementId inMovementId) {}
}
