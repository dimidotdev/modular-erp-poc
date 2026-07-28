package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.internal.application.PriceTableSummary;
import java.time.Instant;
import java.time.LocalDate;

/** Response de {@code price_table}. */
public record PriceTableApiResponse(
    PriceTableId id,
    String name,
    BranchId branchId,
    LocalDate validFrom,
    LocalDate validUntil,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static PriceTableApiResponse from(PriceTableSummary s) {
    return new PriceTableApiResponse(
        s.id(),
        s.name(),
        s.branchId(),
        s.validFrom(),
        s.validUntil(),
        s.active(),
        s.createdAt(),
        s.updatedAt());
  }
}
