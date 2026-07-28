package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.internal.application.RecordInventoryAdjustmentCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/movimentacao/inventory-adjustments}. */
public record RecordInventoryAdjustmentApiRequest(
    @NotNull UUID branchId,
    @NotBlank @Size(max = 500) String reason,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<InventoryAdjustmentItemApiRequest> items) {

  public RecordInventoryAdjustmentCommand toCommand(UserId actor) {
    return new RecordInventoryAdjustmentCommand(
        new BranchId(branchId),
        reason,
        notes,
        items.stream().map(InventoryAdjustmentItemApiRequest::toCommand).toList(),
        actor);
  }
}
