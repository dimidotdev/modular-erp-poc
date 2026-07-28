package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import dev.dimi.erp.movimentacao.internal.application.InventoryAdjustmentSummary;
import java.time.Instant;
import java.util.List;

/** Response de ajuste de inventário. */
public record InventoryAdjustmentApiResponse(
    InventoryAdjustmentId id,
    long number,
    BranchId branchId,
    Instant issuedAt,
    String reason,
    String notes,
    UserId userId,
    Instant createdAt,
    List<InventoryAdjustmentItemApiResponse> items) {

  public static InventoryAdjustmentApiResponse from(InventoryAdjustmentSummary s) {
    return new InventoryAdjustmentApiResponse(
        s.id(),
        s.number(),
        s.branchId(),
        s.issuedAt(),
        s.reason(),
        s.notes(),
        s.userId(),
        s.createdAt(),
        s.items().stream().map(InventoryAdjustmentItemApiResponse::from).toList());
  }
}
