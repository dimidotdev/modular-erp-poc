package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustmentItem;
import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustmentItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Snapshot de linha de ajuste. {@code delta} é GENERATED no DDL ({@code counted - expected}). */
public record InventoryAdjustmentItemSummary(
    InventoryAdjustmentItemId id,
    InventoryAdjustmentId adjustmentId,
    PackagingId packagingId,
    StockLocationId locationId,
    BigDecimal expectedQty,
    BigDecimal countedQty,
    BigDecimal delta) {

  public static InventoryAdjustmentItemSummary of(InventoryAdjustmentItem i) {
    return new InventoryAdjustmentItemSummary(
        i.id(),
        i.adjustmentIdTyped(),
        i.packagingIdTyped(),
        i.locationIdTyped(),
        i.getExpectedQty(),
        i.getCountedQty(),
        i.getDelta());
  }
}
