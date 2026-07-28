package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.SaleReturnId;
import dev.dimi.erp.movimentacao.internal.application.SaleReturnItemSummary;
import dev.dimi.erp.movimentacao.internal.domain.SaleItemId;
import dev.dimi.erp.movimentacao.internal.domain.SaleReturnItemId;
import java.math.BigDecimal;

/** Response de linha de devolução. */
public record SaleReturnItemApiResponse(
    SaleReturnItemId id,
    SaleReturnId returnId,
    SaleItemId saleItemId,
    BigDecimal quantityReturned,
    BigDecimal quantityLost,
    BigDecimal quantityUsed,
    BigDecimal refundValue) {

  public static SaleReturnItemApiResponse from(SaleReturnItemSummary s) {
    return new SaleReturnItemApiResponse(
        s.id(),
        s.returnId(),
        s.saleItemId(),
        s.quantityReturned(),
        s.quantityLost(),
        s.quantityUsed(),
        s.refundValue());
  }
}
