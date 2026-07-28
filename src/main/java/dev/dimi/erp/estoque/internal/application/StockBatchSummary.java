package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.internal.domain.StockBatch;
import dev.dimi.erp.produto.PackagingId;
import java.time.Instant;
import java.time.LocalDate;

/** Snapshot read-only de {@link StockBatch}. */
public record StockBatchSummary(
    StockBatchId id,
    PackagingId packagingId,
    String code,
    LocalDate manufacturedOn,
    LocalDate expiresOn,
    String notes,
    Instant createdAt,
    Instant updatedAt) {

  public static StockBatchSummary of(StockBatch b) {
    return new StockBatchSummary(
        b.id(),
        b.packagingIdTyped(),
        b.getCode(),
        b.getManufacturedOn(),
        b.getExpiresOn(),
        b.getNotes(),
        b.getCreatedAt(),
        b.getUpdatedAt());
  }
}
