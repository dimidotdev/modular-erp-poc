package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.movimentacao.TransferStatus;
import dev.dimi.erp.movimentacao.internal.domain.StockTransfer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot de {@link StockTransfer} com itens. */
public record StockTransferSummary(
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
    List<StockTransferItemSummary> items) {

  public static StockTransferSummary of(StockTransfer t, List<StockTransferItemSummary> items) {
    return new StockTransferSummary(
        t.id(),
        t.getNumber(),
        t.originBranchIdTyped(),
        t.destBranchIdTyped(),
        t.originLocationIdTyped(),
        t.destLocationIdTyped(),
        t.getStatus(),
        t.getIssuedAt(),
        t.getClosedAt(),
        t.getInvoiceId(),
        t.getNotes(),
        t.getCreatedAt(),
        t.getUpdatedAt(),
        items);
  }
}
