package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.internal.application.UpdateSalesOrderCommand;
import dev.dimi.erp.produto.PriceTableId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code PATCH /api/v1/movimentacao/sales-orders/{id}}. Quando {@code items} é null no
 * payload, preserva os itens existentes; quando informado (mesmo vazio), substitui o conjunto.
 */
public record UpdateSalesOrderApiRequest(
    UUID salespersonId,
    UUID paymentConditionId,
    UUID priceTableId,
    LocalDate validUntil,
    @Size(max = 2000) String notes,
    @Valid List<SalesOrderItemApiRequest> items) {

  public UpdateSalesOrderCommand toCommand(SalesOrderId salesOrderId, UserId actor) {
    return new UpdateSalesOrderCommand(
        salesOrderId,
        salespersonId == null ? null : new PersonId(salespersonId),
        paymentConditionId,
        priceTableId == null ? null : new PriceTableId(priceTableId),
        validUntil,
        notes,
        items == null ? null : items.stream().map(SalesOrderItemApiRequest::toCommand).toList(),
        actor);
  }
}
