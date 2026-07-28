package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.movimentacao.ReturnStatus;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleReturnId;
import dev.dimi.erp.movimentacao.internal.domain.SaleReturn;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot de {@link SaleReturn} com itens. */
public record SaleReturnSummary(
    SaleReturnId id,
    SaleId saleId,
    BranchId branchId,
    ReturnStatus status,
    Instant issuedAt,
    Instant closedAt,
    String reason,
    BigDecimal total,
    UUID invoiceId,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    List<SaleReturnItemSummary> items) {

  public static SaleReturnSummary of(SaleReturn r, List<SaleReturnItemSummary> items) {
    return new SaleReturnSummary(
        r.id(),
        r.saleIdTyped(),
        r.branchIdTyped(),
        r.getStatus(),
        r.getIssuedAt(),
        r.getClosedAt(),
        r.getReason(),
        r.getTotal(),
        r.getInvoiceId(),
        r.getNotes(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        items);
  }
}
