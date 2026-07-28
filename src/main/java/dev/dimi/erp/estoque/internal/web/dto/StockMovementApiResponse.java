package dev.dimi.erp.estoque.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.Direction;
import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.internal.domain.StockMovement;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response de {@code stock_movement}. */
public record StockMovementApiResponse(
    MovementId id,
    Direction direction,
    String reason,
    PackagingId packagingId,
    BranchId branchId,
    StockLocationId locationId,
    StockBatchId batchId,
    BigDecimal quantity,
    BigDecimal unitCost,
    BigDecimal totalCost,
    String originType,
    UUID originId,
    String idempotencyKey,
    UserId userId,
    Instant occurredAt,
    Instant createdAt) {

  public static StockMovementApiResponse from(StockMovement m) {
    return new StockMovementApiResponse(
        m.id(),
        m.getDirection(),
        m.getReason(),
        m.packagingIdTyped(),
        m.branchIdTyped(),
        m.locationIdTyped(),
        m.batchIdTyped(),
        m.getQuantity(),
        m.getUnitCost(),
        m.getTotalCost(),
        m.getOriginType(),
        m.getOriginId(),
        m.getIdempotencyKey(),
        m.userIdTyped(),
        m.getOccurredAt(),
        m.getCreatedAt());
  }
}
