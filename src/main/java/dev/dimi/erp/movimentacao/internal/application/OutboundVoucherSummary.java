package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.OutboundReason;
import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucher;
import java.time.Instant;
import java.util.List;

/** Snapshot de {@link OutboundVoucher} com itens. */
public record OutboundVoucherSummary(
    OutboundVoucherId id,
    long number,
    BranchId branchId,
    OutboundReason reason,
    Instant issuedAt,
    PersonId recipientPersonId,
    String notes,
    Instant createdAt,
    List<OutboundVoucherItemSummary> items) {

  public static OutboundVoucherSummary of(
      OutboundVoucher v, List<OutboundVoucherItemSummary> items) {
    return new OutboundVoucherSummary(
        v.id(),
        v.getNumber(),
        v.branchIdTyped(),
        v.getReason(),
        v.getIssuedAt(),
        v.recipientPersonIdTyped(),
        v.getNotes(),
        v.getCreatedAt(),
        items);
  }
}
