package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.Priority;
import dev.dimi.erp.compras.internal.application.RegisterPurchaseRequestCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/requests}. */
public record CreatePurchaseRequestApiRequest(
    @NotNull UUID branchId,
    UUID departmentId,
    UUID costCenterId,
    LocalDate neededBy,
    Priority priority,
    @Size(max = 2000) String justification,
    @NotEmpty @Valid List<PurchaseRequestItemApiRequest> items) {

  public RegisterPurchaseRequestCommand toCommand(UserId actor) {
    return new RegisterPurchaseRequestCommand(
        new BranchId(branchId),
        departmentId,
        costCenterId,
        neededBy,
        priority,
        justification,
        items.stream().map(PurchaseRequestItemApiRequest::toCommand).toList(),
        actor);
  }
}
