package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.internal.application.OpenStockTransferCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/movimentacao/transfers}. */
public record OpenStockTransferApiRequest(
    @NotNull UUID originBranchId,
    @NotNull UUID destBranchId,
    UUID originLocationId,
    UUID destLocationId,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<StockTransferItemApiRequest> items) {

  public OpenStockTransferCommand toCommand(UserId actor) {
    return new OpenStockTransferCommand(
        new BranchId(originBranchId),
        new BranchId(destBranchId),
        originLocationId == null ? null : new StockLocationId(originLocationId),
        destLocationId == null ? null : new StockLocationId(destLocationId),
        notes,
        items.stream().map(StockTransferItemApiRequest::toCommand).toList(),
        actor);
  }
}
