package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.SupplierReturnId;
import dev.dimi.erp.compras.internal.domain.SupplierReturn;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot de devolução a fornecedor com itens. */
public record SupplierReturnSummary(
    SupplierReturnId id,
    PurchaseReceiptId receiptId,
    long number,
    Instant issuedAt,
    String reason,
    BigDecimal total,
    UUID invoiceId,
    String notes,
    Instant createdAt,
    List<SupplierReturnItemSummary> items) {

  public static SupplierReturnSummary of(SupplierReturn r, List<SupplierReturnItemSummary> items) {
    return new SupplierReturnSummary(
        r.id(),
        r.receiptIdTyped(),
        r.getNumber(),
        r.getIssuedAt(),
        r.getReason(),
        r.getTotal(),
        r.getInvoiceId(),
        r.getNotes(),
        r.getCreatedAt(),
        items);
  }
}
