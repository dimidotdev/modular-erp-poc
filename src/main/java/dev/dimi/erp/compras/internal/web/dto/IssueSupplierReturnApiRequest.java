package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.internal.application.IssueSupplierReturnCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/supplier-returns}. */
public record IssueSupplierReturnApiRequest(
    @NotNull UUID receiptId,
    @NotBlank @Size(max = 2000) String reason,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<IssueSupplierReturnItemApiRequest> items) {

  public IssueSupplierReturnCommand toCommand(UserId actor) {
    return new IssueSupplierReturnCommand(
        new PurchaseReceiptId(receiptId),
        reason,
        notes,
        items.stream().map(IssueSupplierReturnItemApiRequest::toCommand).toList(),
        actor);
  }
}
