package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustment;
import java.time.Instant;
import java.util.List;

/** Snapshot de {@link InventoryAdjustment} com itens. */
public record InventoryAdjustmentSummary(
    InventoryAdjustmentId id,
    long number,
    BranchId branchId,
    Instant issuedAt,
    String reason,
    String notes,
    UserId userId,
    Instant createdAt,
    List<InventoryAdjustmentItemSummary> items) {

  public static InventoryAdjustmentSummary of(
      InventoryAdjustment a, List<InventoryAdjustmentItemSummary> items) {
    return new InventoryAdjustmentSummary(
        a.id(),
        a.getNumber(),
        a.branchIdTyped(),
        a.getIssuedAt(),
        a.getReason(),
        a.getNotes(),
        a.userIdTyped(),
        a.getCreatedAt(),
        items);
  }
}
