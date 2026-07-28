package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.internal.domain.PriceTable;
import java.time.Instant;
import java.time.LocalDate;

/** Snapshot read-only de {@link PriceTable}. */
public record PriceTableSummary(
    PriceTableId id,
    String name,
    BranchId branchId,
    LocalDate validFrom,
    LocalDate validUntil,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static PriceTableSummary of(PriceTable t) {
    return new PriceTableSummary(
        t.id(),
        t.getName(),
        t.getBranchId() == null ? null : new BranchId(t.getBranchId()),
        t.getValidFrom(),
        t.getValidUntil(),
        t.isActive(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }
}
