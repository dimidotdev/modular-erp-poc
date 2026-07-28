package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.application.RegisterQuotationResponseCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/quotations/{id}/responses}. */
public record RegisterQuotationResponseApiRequest(
    @NotNull UUID supplierId, @NotEmpty @Valid List<QuotationResponseLineApiRequest> lines) {

  public RegisterQuotationResponseCommand toCommand(UUID quotationId, UserId actor) {
    return new RegisterQuotationResponseCommand(
        new QuotationId(quotationId),
        new PersonId(supplierId),
        lines.stream().map(QuotationResponseLineApiRequest::toCommand).toList(),
        actor);
  }
}
