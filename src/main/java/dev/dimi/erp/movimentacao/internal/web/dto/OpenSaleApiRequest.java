package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.internal.application.OpenSaleCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code POST /api/v1/movimentacao/sales}. {@code idempotencyKey} vem do header
 * Idempotency-Key (RN-MOV-004 / arquitetura 08).
 */
public record OpenSaleApiRequest(
    @NotNull UUID branchId,
    @NotNull UUID customerId,
    UUID salespersonId,
    UUID paymentConditionId,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<SaleItemApiRequest> items) {

  public OpenSaleCommand toCommand(String idempotencyKey, UserId actor) {
    return new OpenSaleCommand(
        new BranchId(branchId),
        new PersonId(customerId),
        salespersonId == null ? null : new PersonId(salespersonId),
        paymentConditionId,
        idempotencyKey,
        notes,
        items.stream().map(SaleItemApiRequest::toCommand).toList(),
        actor);
  }
}
