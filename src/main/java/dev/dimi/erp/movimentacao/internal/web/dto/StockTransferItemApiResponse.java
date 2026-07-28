package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.movimentacao.internal.application.StockTransferItemSummary;
import dev.dimi.erp.movimentacao.internal.domain.StockTransferItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de linha de transferência. */
public record StockTransferItemApiResponse(
    StockTransferItemId id,
    StockTransferId transferId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity) {

  public static StockTransferItemApiResponse from(StockTransferItemSummary s) {
    return new StockTransferItemApiResponse(
        s.id(), s.transferId(), s.lineNumber(), s.packagingId(), s.quantity());
  }
}
