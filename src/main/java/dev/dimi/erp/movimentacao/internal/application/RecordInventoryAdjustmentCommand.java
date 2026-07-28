package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;

/**
 * Comando de registro de ajuste de inventário batch. Cada linha tem {@code expected} (saldo
 * esperado em sistema) vs {@code counted} (saldo contado fisicamente); o delta {@code counted -
 * expected} dispara {@code StockInventoryPort.adjust} no estoque (RN-MOV-024).
 */
public record RecordInventoryAdjustmentCommand(
    BranchId branchId, String reason, String notes, List<Item> items, UserId actor) {

  public record Item(
      PackagingId packagingId,
      StockLocationId locationId,
      BigDecimal expectedQty,
      BigDecimal countedQty) {}
}
