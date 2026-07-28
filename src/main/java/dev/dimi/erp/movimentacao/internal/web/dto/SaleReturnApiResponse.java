package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.movimentacao.ReturnStatus;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleReturnId;
import dev.dimi.erp.movimentacao.internal.application.SaleReturnSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response de devolução. */
public record SaleReturnApiResponse(
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
    List<SaleReturnItemApiResponse> items) {

  public static SaleReturnApiResponse from(SaleReturnSummary s) {
    return new SaleReturnApiResponse(
        s.id(),
        s.saleId(),
        s.branchId(),
        s.status(),
        s.issuedAt(),
        s.closedAt(),
        s.reason(),
        s.total(),
        s.invoiceId(),
        s.notes(),
        s.createdAt(),
        s.updatedAt(),
        s.items().stream().map(SaleReturnItemApiResponse::from).toList());
  }
}
