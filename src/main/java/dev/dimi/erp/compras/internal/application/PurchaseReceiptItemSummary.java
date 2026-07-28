package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItemId;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItem;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItemId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import java.math.BigDecimal;

/** Snapshot de linha de recebimento. */
public record PurchaseReceiptItemSummary(
    PurchaseReceiptItemId id,
    PurchaseReceiptId receiptId,
    PurchaseOrderItemId orderItemId,
    BigDecimal quantity,
    BigDecimal unitCost,
    StockLocationId locationId,
    StockBatchId batchId) {

  public static PurchaseReceiptItemSummary of(PurchaseReceiptItem i) {
    return new PurchaseReceiptItemSummary(
        i.id(),
        i.receiptIdTyped(),
        i.orderItemIdTyped(),
        i.getQuantity(),
        i.getUnitCost(),
        i.locationIdTyped(),
        i.batchIdTyped());
  }
}
