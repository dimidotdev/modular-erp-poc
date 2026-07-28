package dev.dimi.erp.estoque.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.estoque.StockSnapshotId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de {@code GET /api/v1/estoque/snapshots}. */
public record StockSnapshotApiResponse(
    StockSnapshotId id,
    PackagingId packagingId,
    BranchId branchId,
    StockLocationId locationId,
    StockBatchId batchId,
    BigDecimal quantityOnHand,
    BigDecimal quantityReserved,
    BigDecimal quantityAvailable,
    BigDecimal weightedAvgCost) {

  public static StockSnapshotApiResponse from(StockQueryPort.StockSnapshotView v) {
    return new StockSnapshotApiResponse(
        v.id(),
        v.packagingId(),
        v.branchId(),
        v.locationId(),
        v.batchId(),
        v.quantityOnHand(),
        v.quantityReserved(),
        v.quantityAvailable(),
        v.weightedAvgCost());
  }
}
