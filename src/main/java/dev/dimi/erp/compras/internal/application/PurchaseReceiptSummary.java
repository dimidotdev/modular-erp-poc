package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.internal.domain.PurchaseReceipt;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot de recebimento com linhas. */
public record PurchaseReceiptSummary(
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
    List<PurchaseReceiptItemSummary> items) {

  public static PurchaseReceiptSummary of(
      PurchaseReceipt r, List<PurchaseReceiptItemSummary> items) {
    return new PurchaseReceiptSummary(
        r.id(),
        r.orderIdTyped(),
        r.getNumber(),
        r.getReceivedAt(),
        r.getSupplierInvoiceKey(),
        r.getSupplierInvoiceNumber(),
        r.getDfeDocumentId(),
        r.getFreightValue(),
        r.getTotal(),
        r.getNotes(),
        r.userIdTyped(),
        r.getCreatedAt(),
        items);
  }
}
