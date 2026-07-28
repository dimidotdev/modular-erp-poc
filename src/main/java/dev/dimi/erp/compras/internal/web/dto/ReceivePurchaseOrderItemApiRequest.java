package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.internal.application.ReceivePurchaseOrderCommand;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItemId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de recebimento em {@code POST /api/v1/compras/orders/{id}/receive}. */
public record ReceivePurchaseOrderItemApiRequest(
    @NotNull UUID orderItemId,
    @NotNull @Positive BigDecimal quantity,
    @NotNull @Positive BigDecimal unitCost,
    UUID locationId,
    UUID batchId) {

  public ReceivePurchaseOrderCommand.Item toCommand() {
    return new ReceivePurchaseOrderCommand.Item(
        new PurchaseOrderItemId(orderItemId),
        quantity,
        unitCost,
        locationId == null ? null : new StockLocationId(locationId),
        batchId == null ? null : new StockBatchId(batchId));
  }
}
