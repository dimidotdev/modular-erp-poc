package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.internal.application.PurchaseReceiptItemSummary;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItemId;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItemId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import java.math.BigDecimal;

/** Response de linha de recebimento. */
public record PurchaseReceiptItemApiResponse(
    PurchaseReceiptItemId id,
    PurchaseReceiptId receiptId,
    PurchaseOrderItemId orderItemId,
    BigDecimal quantity,
    BigDecimal unitCost,
    StockLocationId locationId,
    StockBatchId batchId) {

  public static PurchaseReceiptItemApiResponse from(PurchaseReceiptItemSummary s) {
    return new PurchaseReceiptItemApiResponse(
        s.id(),
        s.receiptId(),
        s.orderItemId(),
        s.quantity(),
        s.unitCost(),
        s.locationId(),
        s.batchId());
  }
}
