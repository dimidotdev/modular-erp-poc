package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.application.OpenPurchaseOrderCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/orders} (PC ad-hoc, sem cotação/SC). */
public record OpenPurchaseOrderApiRequest(
    @NotNull UUID branchId,
    @NotNull UUID supplierId,
    UUID quotationId,
    UUID requestId,
    UUID paymentConditionId,
    LocalDate deliveryDateExpected,
    @PositiveOrZero BigDecimal totalFreight,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<OpenPurchaseOrderItemApiRequest> items) {

  public OpenPurchaseOrderCommand toCommand(UserId actor) {
    return new OpenPurchaseOrderCommand(
        new BranchId(branchId),
        new PersonId(supplierId),
        quotationId == null ? null : new QuotationId(quotationId),
        requestId == null ? null : new PurchaseRequestId(requestId),
        paymentConditionId,
        deliveryDateExpected,
        totalFreight,
        notes,
        items.stream().map(OpenPurchaseOrderItemApiRequest::toCommand).toList(),
        actor);
  }
}
