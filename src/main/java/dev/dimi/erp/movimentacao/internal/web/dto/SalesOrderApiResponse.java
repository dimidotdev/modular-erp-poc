package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.movimentacao.internal.application.SalesOrderSummary;
import dev.dimi.erp.produto.PriceTableId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response de pedido com itens. */
public record SalesOrderApiResponse(
    SalesOrderId id,
    long version,
    BranchId branchId,
    PersonId customerId,
    PersonId salespersonId,
    UUID paymentConditionId,
    PriceTableId priceTableId,
    long number,
    SalesOrderStatus status,
    LocalDate validUntil,
    BigDecimal totalGross,
    BigDecimal totalDiscount,
    BigDecimal total,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    Instant convertedAt,
    SaleId convertedSaleId,
    List<SalesOrderItemApiResponse> items) {

  public static SalesOrderApiResponse from(SalesOrderSummary s) {
    return new SalesOrderApiResponse(
        s.id(),
        s.version(),
        s.branchId(),
        s.customerId(),
        s.salespersonId(),
        s.paymentConditionId(),
        s.priceTableId(),
        s.number(),
        s.status(),
        s.validUntil(),
        s.totalGross(),
        s.totalDiscount(),
        s.total(),
        s.notes(),
        s.createdAt(),
        s.updatedAt(),
        s.convertedAt(),
        s.convertedSaleId(),
        s.items().stream().map(SalesOrderItemApiResponse::from).toList());
  }
}
