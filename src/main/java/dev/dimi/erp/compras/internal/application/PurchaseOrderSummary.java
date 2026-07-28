package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseOrderStatus;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.domain.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Snapshot de PC com itens. */
public record PurchaseOrderSummary(
    PurchaseOrderId id,
    long version,
    long number,
    BranchId branchId,
    PersonId supplierId,
    QuotationId quotationId,
    PurchaseRequestId requestId,
    UUID paymentConditionId,
    PurchaseOrderStatus status,
    LocalDate deliveryDateExpected,
    BigDecimal totalProducts,
    BigDecimal totalFreight,
    BigDecimal total,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    List<PurchaseOrderItemSummary> items) {

  public static PurchaseOrderSummary of(PurchaseOrder o, List<PurchaseOrderItemSummary> items) {
    return new PurchaseOrderSummary(
        o.id(),
        o.getVersion(),
        o.getNumber(),
        o.branchIdTyped(),
        o.supplierIdTyped(),
        o.quotationIdTyped(),
        o.requestIdTyped(),
        o.getPaymentConditionId(),
        o.getStatus(),
        o.getDeliveryDateExpected(),
        o.getTotalProducts(),
        o.getTotalFreight(),
        o.getTotal(),
        o.getNotes(),
        o.getCreatedAt(),
        o.getUpdatedAt(),
        items);
  }
}
