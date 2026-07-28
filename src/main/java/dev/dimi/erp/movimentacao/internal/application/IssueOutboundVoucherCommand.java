package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.OutboundReason;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;

/** Comando de emissão de saída avulsa. {@code recipientPersonId} opcional. */
public record IssueOutboundVoucherCommand(
    BranchId branchId,
    OutboundReason reason,
    PersonId recipientPersonId,
    String notes,
    List<Item> items,
    UserId actor) {

  public record Item(PackagingId packagingId, BigDecimal quantity) {}
}
