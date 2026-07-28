package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.SupplierReturnId;
import dev.dimi.erp.compras.internal.application.SupplierReturnSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response de devolução a fornecedor com itens. */
public record SupplierReturnApiResponse(
    SupplierReturnId id,
    PurchaseReceiptId receiptId,
    long number,
    Instant issuedAt,
    String reason,
    BigDecimal total,
    UUID invoiceId,
    String notes,
    Instant createdAt,
    List<SupplierReturnItemApiResponse> items) {

  public static SupplierReturnApiResponse from(SupplierReturnSummary s) {
    return new SupplierReturnApiResponse(
        s.id(),
        s.receiptId(),
        s.number(),
        s.issuedAt(),
        s.reason(),
        s.total(),
        s.invoiceId(),
        s.notes(),
        s.createdAt(),
        s.items().stream().map(SupplierReturnItemApiResponse::from).toList());
  }
}
