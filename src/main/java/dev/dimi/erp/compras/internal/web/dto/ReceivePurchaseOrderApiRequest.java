package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.internal.application.ReceivePurchaseOrderCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/compras/orders/{id}/receive}. */
public record ReceivePurchaseOrderApiRequest(
    @Size(max = 44) String supplierInvoiceKey,
    @Size(max = 30) String supplierInvoiceNumber,
    UUID dfeDocumentId,
    @PositiveOrZero BigDecimal freightValue,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<ReceivePurchaseOrderItemApiRequest> items,
    boolean allowExcess) {

  public ReceivePurchaseOrderCommand toCommand(UUID orderId, String idempotencyKey, UserId actor) {
    return new ReceivePurchaseOrderCommand(
        new PurchaseOrderId(orderId),
        supplierInvoiceKey,
        supplierInvoiceNumber,
        dfeDocumentId,
        freightValue,
        notes,
        items.stream().map(ReceivePurchaseOrderItemApiRequest::toCommand).toList(),
        idempotencyKey,
        actor,
        allowExcess);
  }
}
