package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.internal.application.SaleSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response de venda com itens. */
public record SaleApiResponse(
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
    List<SaleItemApiResponse> items) {

  public static SaleApiResponse from(SaleSummary s) {
    return new SaleApiResponse(
        s.id(),
        s.version(),
        s.branchId(),
        s.customerId(),
        s.salespersonId(),
        s.originSalesOrderId(),
        s.paymentConditionId(),
        s.number(),
        s.status(),
        s.issuedAt(),
        s.closedAt(),
        s.cancelledAt(),
        s.cancellationReason(),
        s.totalGross(),
        s.totalDiscount(),
        s.totalFreight(),
        s.total(),
        s.invoiceId(),
        s.notes(),
        s.idempotencyKey(),
        s.createdAt(),
        s.updatedAt(),
        s.items().stream().map(SaleItemApiResponse::from).toList());
  }
}
