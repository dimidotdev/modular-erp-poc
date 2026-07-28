package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.application.InviteSupplierCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/quotations/{id}/suppliers}. */
public record InviteSupplierApiRequest(
    @NotNull UUID supplierId,
    UUID paymentConditionId,
    @PositiveOrZero Integer deliveryDays,
    @PositiveOrZero BigDecimal freightValue,
    @Size(max = 2000) String notes) {

  public InviteSupplierCommand toCommand(UUID quotationId, UserId actor) {
    return new InviteSupplierCommand(
        new QuotationId(quotationId),
        new PersonId(supplierId),
        paymentConditionId,
        deliveryDays,
        freightValue,
        notes,
        actor);
  }
}
