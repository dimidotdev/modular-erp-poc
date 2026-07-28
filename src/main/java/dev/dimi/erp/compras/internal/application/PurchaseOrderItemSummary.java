package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItem;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Snapshot de linha de PC. */
public record PurchaseOrderItemSummary(
    PurchaseOrderItemId id,
    PurchaseOrderId orderId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantityOrdered,
    BigDecimal quantityReceived,
    BigDecimal unitPrice,
    BigDecimal discountValue,
    BigDecimal netValue,
    LocalDate expectedDelivery) {

  public static PurchaseOrderItemSummary of(PurchaseOrderItem i) {
    return new PurchaseOrderItemSummary(
        i.id(),
        i.orderIdTyped(),
        i.getLineNumber(),
        i.packagingIdTyped(),
        i.getQuantityOrdered(),
        i.getQuantityReceived(),
        i.getUnitPrice(),
        i.getDiscountValue(),
        i.getNetValue(),
        i.getExpectedDelivery());
  }
}
