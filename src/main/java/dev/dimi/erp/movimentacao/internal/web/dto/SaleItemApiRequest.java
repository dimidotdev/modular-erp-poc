package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.internal.application.OpenSaleCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de venda em POST. */
public record SaleItemApiRequest(
    @NotNull UUID packagingId,
    @NotNull @Positive BigDecimal quantity,
    @PositiveOrZero BigDecimal unitPrice,
    @PositiveOrZero BigDecimal discountValue) {

  public OpenSaleCommand.Item toCommand() {
    return new OpenSaleCommand.Item(
        new PackagingId(packagingId), quantity, unitPrice, discountValue);
  }
}
