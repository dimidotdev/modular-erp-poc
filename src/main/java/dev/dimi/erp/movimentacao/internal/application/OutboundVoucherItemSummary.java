package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucherItem;
import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucherItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Snapshot de linha de saída avulsa. */
public record OutboundVoucherItemSummary(
    OutboundVoucherItemId id,
    OutboundVoucherId voucherId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity) {

  public static OutboundVoucherItemSummary of(OutboundVoucherItem i) {
    return new OutboundVoucherItemSummary(
        i.id(), i.voucherIdTyped(), i.getLineNumber(), i.packagingIdTyped(), i.getQuantity());
  }
}
