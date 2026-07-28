package dev.dimi.erp.estoque.internal.web.dto;

import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.internal.application.StockBatchSummary;
import dev.dimi.erp.produto.PackagingId;
import java.time.Instant;
import java.time.LocalDate;

/** Response de {@code stock_batch}. */
public record StockBatchApiResponse(
    StockBatchId id,
    PackagingId packagingId,
    String code,
    LocalDate manufacturedOn,
    LocalDate expiresOn,
    String notes,
    Instant createdAt,
    Instant updatedAt) {

  public static StockBatchApiResponse from(StockBatchSummary s) {
    return new StockBatchApiResponse(
        s.id(),
        s.packagingId(),
        s.code(),
        s.manufacturedOn(),
        s.expiresOn(),
        s.notes(),
        s.createdAt(),
        s.updatedAt());
  }
}
