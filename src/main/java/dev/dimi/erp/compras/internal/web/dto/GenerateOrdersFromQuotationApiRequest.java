package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.application.GenerateOrdersFromQuotationCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/orders/from-quotation/{id}}. */
public record GenerateOrdersFromQuotationApiRequest(
    @NotEmpty @Valid List<SupplierSelectionApiRequest> selections) {

  public GenerateOrdersFromQuotationCommand toCommand(UUID quotationId, UserId actor) {
    return new GenerateOrdersFromQuotationCommand(
        new QuotationId(quotationId),
        selections.stream().map(SupplierSelectionApiRequest::toCommand).toList(),
        actor);
  }
}
