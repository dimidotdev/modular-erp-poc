package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SaleReturnId;
import dev.dimi.erp.movimentacao.internal.domain.SaleItemId;
import dev.dimi.erp.movimentacao.internal.domain.SaleReturnItem;
import dev.dimi.erp.movimentacao.internal.domain.SaleReturnItemId;
import java.math.BigDecimal;

/** Snapshot de linha de devolução com as 3 quantidades. */
public record SaleReturnItemSummary(
    SaleReturnItemId id,
    SaleReturnId returnId,
    SaleItemId saleItemId,
    BigDecimal quantityReturned,
    BigDecimal quantityLost,
    BigDecimal quantityUsed,
    BigDecimal refundValue) {

  public static SaleReturnItemSummary of(SaleReturnItem i) {
    return new SaleReturnItemSummary(
        i.id(),
        i.returnIdTyped(),
        i.saleItemIdTyped(),
        i.getQuantityReturned(),
        i.getQuantityLost(),
        i.getQuantityUsed(),
        i.getRefundValue());
  }
}
