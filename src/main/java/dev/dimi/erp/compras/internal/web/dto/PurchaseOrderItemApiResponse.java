package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.internal.application.PurchaseOrderItemSummary;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Response de linha de PC. */
public record PurchaseOrderItemApiResponse(
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

  public static PurchaseOrderItemApiResponse from(PurchaseOrderItemSummary s) {
    return new PurchaseOrderItemApiResponse(
        s.id(),
        s.orderId(),
        s.lineNumber(),
        s.packagingId(),
        s.quantityOrdered(),
        s.quantityReceived(),
        s.unitPrice(),
        s.discountValue(),
        s.netValue(),
        s.expectedDelivery());
  }
}
