package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrder;
import dev.dimi.erp.produto.PriceTableId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Snapshot read-only de {@link SalesOrder} com seus itens. */
public record SalesOrderSummary(
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
    List<SalesOrderItemSummary> items) {

  public static SalesOrderSummary of(SalesOrder o, List<SalesOrderItemSummary> items) {
    return new SalesOrderSummary(
        o.id(),
        o.getVersion(),
        o.branchIdTyped(),
        o.customerIdTyped(),
        o.salespersonIdTyped(),
        o.getPaymentConditionId(),
        o.priceTableIdTyped(),
        o.getNumber(),
        o.getStatus(),
        o.getValidUntil(),
        o.getTotalGross(),
        o.getTotalDiscount(),
        o.getTotal(),
        o.getNotes(),
        o.getCreatedAt(),
        o.getUpdatedAt(),
        o.getConvertedAt(),
        o.convertedSaleIdTyped(),
        items);
  }
}
