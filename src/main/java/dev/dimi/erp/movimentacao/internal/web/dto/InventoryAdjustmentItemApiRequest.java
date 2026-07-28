package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.movimentacao.internal.application.RecordInventoryAdjustmentCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de ajuste de inventário (conferência). */
public record InventoryAdjustmentItemApiRequest(
    @NotNull UUID packagingId,
    @NotNull UUID locationId,
    @NotNull @PositiveOrZero BigDecimal expectedQty,
    @NotNull @PositiveOrZero BigDecimal countedQty) {

  public RecordInventoryAdjustmentCommand.Item toCommand() {
    return new RecordInventoryAdjustmentCommand.Item(
        new PackagingId(packagingId), new StockLocationId(locationId), expectedQty, countedQty);
  }
}
