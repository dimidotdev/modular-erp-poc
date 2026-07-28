package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.internal.application.OpenPurchaseOrderCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Linha de PC ad-hoc em {@code POST /api/v1/compras/orders}. */
public record OpenPurchaseOrderItemApiRequest(
    @NotNull UUID packagingId,
    @NotNull @Positive BigDecimal quantity,
    @NotNull @Positive BigDecimal unitPrice,
    @PositiveOrZero BigDecimal discountValue,
    LocalDate expectedDelivery) {

  public OpenPurchaseOrderCommand.Item toCommand() {
    return new OpenPurchaseOrderCommand.Item(
        new PackagingId(packagingId), quantity, unitPrice, discountValue, expectedDelivery);
  }
}
