package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.OutboundReason;
import dev.dimi.erp.movimentacao.internal.application.IssueOutboundVoucherCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/movimentacao/outbound-vouchers}. */
public record IssueOutboundVoucherApiRequest(
    @NotNull UUID branchId,
    @NotNull OutboundReason reason,
    UUID recipientPersonId,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<OutboundVoucherItemApiRequest> items) {

  public IssueOutboundVoucherCommand toCommand(UserId actor) {
    return new IssueOutboundVoucherCommand(
        new BranchId(branchId),
        reason,
        recipientPersonId == null ? null : new PersonId(recipientPersonId),
        notes,
        items.stream().map(OutboundVoucherItemApiRequest::toCommand).toList(),
        actor);
  }
}
