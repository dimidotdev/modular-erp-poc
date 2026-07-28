package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.OutboundReason;
import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.movimentacao.internal.application.OutboundVoucherSummary;
import java.time.Instant;
import java.util.List;

/** Response de saída avulsa. */
public record OutboundVoucherApiResponse(
    OutboundVoucherId id,
    long number,
    BranchId branchId,
    OutboundReason reason,
    Instant issuedAt,
    PersonId recipientPersonId,
    String notes,
    Instant createdAt,
    List<OutboundVoucherItemApiResponse> items) {

  public static OutboundVoucherApiResponse from(OutboundVoucherSummary s) {
    return new OutboundVoucherApiResponse(
        s.id(),
        s.number(),
        s.branchId(),
        s.reason(),
        s.issuedAt(),
        s.recipientPersonId(),
        s.notes(),
        s.createdAt(),
        s.items().stream().map(OutboundVoucherItemApiResponse::from).toList());
  }
}
