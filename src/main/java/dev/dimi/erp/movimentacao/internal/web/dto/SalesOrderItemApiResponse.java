package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.internal.application.SalesOrderItemSummary;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrderItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de linha de pedido. */
public record SalesOrderItemApiResponse(
    SalesOrderItemId id,
    SalesOrderId orderId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountPct,
    BigDecimal discountValue,
    BigDecimal netValue) {

  public static SalesOrderItemApiResponse from(SalesOrderItemSummary s) {
    return new SalesOrderItemApiResponse(
        s.id(),
        s.orderId(),
        s.lineNumber(),
        s.packagingId(),
        s.quantity(),
        s.unitPrice(),
        s.discountPct(),
        s.discountValue(),
        s.netValue());
  }
}
