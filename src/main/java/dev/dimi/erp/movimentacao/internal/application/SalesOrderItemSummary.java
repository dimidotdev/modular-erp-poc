package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrderItem;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrderItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Snapshot read-only de linha de pedido. */
public record SalesOrderItemSummary(
    SalesOrderItemId id,
    SalesOrderId orderId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountPct,
    BigDecimal discountValue,
    BigDecimal netValue) {

  public static SalesOrderItemSummary of(SalesOrderItem i) {
    return new SalesOrderItemSummary(
        i.id(),
        i.orderIdTyped(),
        i.getLineNumber(),
        i.packagingIdTyped(),
        i.getQuantity(),
        i.getUnitPrice(),
        i.getDiscountPct(),
        i.getDiscountValue(),
        i.getNetValue());
  }
}
