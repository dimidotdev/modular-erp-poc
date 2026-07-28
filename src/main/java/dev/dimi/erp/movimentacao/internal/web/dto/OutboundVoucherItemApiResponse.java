package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.movimentacao.internal.application.OutboundVoucherItemSummary;
import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucherItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de linha de saída avulsa. */
public record OutboundVoucherItemApiResponse(
    OutboundVoucherItemId id,
    OutboundVoucherId voucherId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity) {

  public static OutboundVoucherItemApiResponse from(OutboundVoucherItemSummary s) {
    return new OutboundVoucherItemApiResponse(
        s.id(), s.voucherId(), s.lineNumber(), s.packagingId(), s.quantity());
  }
}
