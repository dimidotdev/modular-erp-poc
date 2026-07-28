package dev.dimi.erp.estoque.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.internal.application.StockLocationSummary;
import java.time.Instant;

/** Response de {@code stock_location}. */
public record StockLocationApiResponse(
    StockLocationId id,
    BranchId branchId,
    String code,
    String name,
    boolean isDefault,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static StockLocationApiResponse from(StockLocationSummary s) {
    return new StockLocationApiResponse(
        s.id(),
        s.branchId(),
        s.code(),
        s.name(),
        s.isDefault(),
        s.active(),
        s.createdAt(),
        s.updatedAt());
  }
}
