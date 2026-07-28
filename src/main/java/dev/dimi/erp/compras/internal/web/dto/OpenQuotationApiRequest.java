package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.internal.application.OpenQuotationCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/quotations}. {@code requestId} null = cotação ad-hoc. */
public record OpenQuotationApiRequest(@NotNull UUID branchId, UUID requestId, LocalDate deadline) {

  public OpenQuotationCommand toCommand(UserId actor) {
    return new OpenQuotationCommand(
        new BranchId(branchId),
        requestId == null ? null : new PurchaseRequestId(requestId),
        deadline,
        actor);
  }
}
