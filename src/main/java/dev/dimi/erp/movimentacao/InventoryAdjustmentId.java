package dev.dimi.erp.movimentacao;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * ID tipado para {@code stock_inventory_adjustment} (ajuste por conferência com expected_qty vs
 * counted_qty). Note que difere do ajuste manual direto via {@code StockInventoryPort.adjust} do
 * módulo estoque — aqui é um documento batch.
 */
public record InventoryAdjustmentId(@JsonValue UUID value) {

  public InventoryAdjustmentId {
    Objects.requireNonNull(value, "inventory adjustment id é obrigatório");
  }

  public static InventoryAdjustmentId newId() {
    return new InventoryAdjustmentId(UuidCreator.getTimeOrderedEpoch());
  }

  public static InventoryAdjustmentId of(String raw) {
    return new InventoryAdjustmentId(UUID.fromString(raw));
  }
}
