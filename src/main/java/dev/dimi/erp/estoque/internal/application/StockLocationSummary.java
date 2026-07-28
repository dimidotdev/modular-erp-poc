package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.internal.domain.StockLocation;
import java.time.Instant;

/** Snapshot read-only de {@link StockLocation}. */
public record StockLocationSummary(
    StockLocationId id,
    BranchId branchId,
    String code,
    String name,
    boolean isDefault,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static StockLocationSummary of(StockLocation l) {
    return new StockLocationSummary(
        l.id(),
        l.branchIdTyped(),
        l.getCode(),
        l.getName(),
        l.isDefault(),
        l.isActive(),
        l.getCreatedAt(),
        l.getUpdatedAt());
  }
}
