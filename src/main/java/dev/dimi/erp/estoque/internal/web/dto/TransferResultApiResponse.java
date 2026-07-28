package dev.dimi.erp.estoque.internal.web.dto;

import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.estoque.StockInventoryPort;

/** Response de {@code POST /api/v1/estoque/transfers}. */
public record TransferResultApiResponse(MovementId outMovementId, MovementId inMovementId) {

  public static TransferResultApiResponse from(StockInventoryPort.TransferResult r) {
    return new TransferResultApiResponse(r.outMovementId(), r.inMovementId());
  }
}
