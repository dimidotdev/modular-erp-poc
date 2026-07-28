package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.movimentacao.internal.domain.StockTransferItem;
import dev.dimi.erp.movimentacao.internal.domain.StockTransferItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Snapshot de linha de transferência. */
public record StockTransferItemSummary(
    StockTransferItemId id,
    StockTransferId transferId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity) {

  public static StockTransferItemSummary of(StockTransferItem i) {
    return new StockTransferItemSummary(
        i.id(), i.transferIdTyped(), i.getLineNumber(), i.packagingIdTyped(), i.getQuantity());
  }
}
