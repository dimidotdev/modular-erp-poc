package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.internal.domain.Sale;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot read-only de {@link Sale} com itens. */
public record SaleSummary(
    SaleId id,
    long version,
    BranchId branchId,
    PersonId customerId,
    PersonId salespersonId,
    SalesOrderId originSalesOrderId,
    UUID paymentConditionId,
    long number,
    SaleStatus status,
    Instant issuedAt,
    Instant closedAt,
    Instant cancelledAt,
    String cancellationReason,
    BigDecimal totalGross,
    BigDecimal totalDiscount,
    BigDecimal totalFreight,
    BigDecimal total,
    UUID invoiceId,
    String notes,
    String idempotencyKey,
    Instant createdAt,
    Instant updatedAt,
    List<SaleItemSummary> items) {

  public static SaleSummary of(Sale s, List<SaleItemSummary> items) {
    return new SaleSummary(
        s.id(),
        s.getVersion(),
        s.branchIdTyped(),
        s.customerIdTyped(),
        s.getSalespersonId() == null ? null : new PersonId(s.getSalespersonId()),
        s.salesOrderIdTyped(),
        s.getPaymentConditionId(),
        s.getNumber(),
        s.getStatus(),
        s.getIssuedAt(),
        s.getClosedAt(),
        s.getCancelledAt(),
        s.getCancellationReason(),
        s.getTotalGross(),
        s.getTotalDiscount(),
        s.getTotalFreight(),
        s.getTotal(),
        s.getInvoiceId(),
        s.getNotes(),
        s.getIdempotencyKey(),
        s.getCreatedAt(),
        s.getUpdatedAt(),
        items);
  }
}
