package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import dev.dimi.erp.movimentacao.internal.application.InventoryAdjustmentItemSummary;
import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustmentItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de linha de ajuste. */
public record InventoryAdjustmentItemApiResponse(
    InventoryAdjustmentItemId id,
    InventoryAdjustmentId adjustmentId,
    PackagingId packagingId,
    StockLocationId locationId,
    BigDecimal expectedQty,
    BigDecimal countedQty,
    BigDecimal delta) {

  public static InventoryAdjustmentItemApiResponse from(InventoryAdjustmentItemSummary s) {
    return new InventoryAdjustmentItemApiResponse(
        s.id(),
        s.adjustmentId(),
        s.packagingId(),
        s.locationId(),
        s.expectedQty(),
        s.countedQty(),
        s.delta());
  }
}
