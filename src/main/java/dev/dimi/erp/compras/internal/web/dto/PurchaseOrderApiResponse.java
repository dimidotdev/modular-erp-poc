package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseOrderStatus;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.application.PurchaseOrderSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response de PC com itens. */
public record PurchaseOrderApiResponse(
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
    List<PurchaseOrderItemApiResponse> items) {

  public static PurchaseOrderApiResponse from(PurchaseOrderSummary s) {
    return new PurchaseOrderApiResponse(
        s.id(),
        s.version(),
        s.number(),
        s.branchId(),
        s.supplierId(),
        s.quotationId(),
        s.requestId(),
        s.paymentConditionId(),
        s.status(),
        s.deliveryDateExpected(),
        s.totalProducts(),
        s.totalFreight(),
        s.total(),
        s.notes(),
        s.createdAt(),
        s.updatedAt(),
        s.items().stream().map(PurchaseOrderItemApiResponse::from).toList());
  }
}
