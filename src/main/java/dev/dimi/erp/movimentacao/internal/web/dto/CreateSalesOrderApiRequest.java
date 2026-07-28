package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.internal.application.RegisterSalesOrderCommand;
import dev.dimi.erp.produto.PriceTableId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/movimentacao/sales-orders}. */
public record CreateSalesOrderApiRequest(
    @NotNull UUID branchId,
    @NotNull UUID customerId,
    UUID salespersonId,
    UUID paymentConditionId,
    UUID priceTableId,
    LocalDate validUntil,
    @Size(max = 2000) String notes,
    @Valid List<SalesOrderItemApiRequest> items) {

  public RegisterSalesOrderCommand toCommand(UserId actor) {
    return new RegisterSalesOrderCommand(
        new BranchId(branchId),
        new PersonId(customerId),
        salespersonId == null ? null : new PersonId(salespersonId),
        paymentConditionId,
        priceTableId == null ? null : new PriceTableId(priceTableId),
        validUntil,
        notes,
        items == null
            ? List.of()
            : items.stream().map(SalesOrderItemApiRequest::toCommand).toList(),
        actor);
  }
}
