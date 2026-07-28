package dev.dimi.erp.movimentacao.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code stock_inventory_adjustment_item}. */
public record InventoryAdjustmentItemId(@JsonValue UUID value) {

  public InventoryAdjustmentItemId {
    Objects.requireNonNull(value, "inventory adjustment item id é obrigatório");
  }

  public static InventoryAdjustmentItemId newId() {
    return new InventoryAdjustmentItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
