package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.internal.application.PurchaseReceiptSummary;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response de recebimento com itens. */
public record PurchaseReceiptApiResponse(
    PurchaseReceiptId id,
    PurchaseOrderId orderId,
    long number,
    Instant receivedAt,
    String supplierInvoiceKey,
    String supplierInvoiceNumber,
    UUID dfeDocumentId,
    BigDecimal freightValue,
    BigDecimal total,
    String notes,
    UserId userId,
    Instant createdAt,
    List<PurchaseReceiptItemApiResponse> items) {

  public static PurchaseReceiptApiResponse from(PurchaseReceiptSummary s) {
    return new PurchaseReceiptApiResponse(
        s.id(),
        s.orderId(),
        s.number(),
        s.receivedAt(),
        s.supplierInvoiceKey(),
        s.supplierInvoiceNumber(),
        s.dfeDocumentId(),
        s.freightValue(),
        s.total(),
        s.notes(),
        s.userId(),
        s.createdAt(),
        s.items().stream().map(PurchaseReceiptItemApiResponse::from).toList());
  }
}
