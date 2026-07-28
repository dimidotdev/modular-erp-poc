package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.movimentacao.TransferStatus;
import dev.dimi.erp.movimentacao.internal.application.StockTransferSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response de transferência. */
public record StockTransferApiResponse(
    StockTransferId id,
    long number,
    BranchId originBranchId,
    BranchId destBranchId,
    StockLocationId originLocationId,
    StockLocationId destLocationId,
    TransferStatus status,
    Instant issuedAt,
    Instant closedAt,
    UUID invoiceId,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    List<StockTransferItemApiResponse> items) {

  public static StockTransferApiResponse from(StockTransferSummary s) {
    return new StockTransferApiResponse(
        s.id(),
        s.number(),
        s.originBranchId(),
        s.destBranchId(),
        s.originLocationId(),
        s.destLocationId(),
        s.status(),
        s.issuedAt(),
        s.closedAt(),
        s.invoiceId(),
        s.notes(),
        s.createdAt(),
        s.updatedAt(),
        s.items().stream().map(StockTransferItemApiResponse::from).toList());
  }
}
