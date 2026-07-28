package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.internal.application.RegisterSalesOrderCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Linha de pedido em POST/PATCH. {@code unitPrice} null → service busca no {@code PriceLookupPort}.
 */
public record SalesOrderItemApiRequest(
    @NotNull UUID packagingId,
    @NotNull @Positive BigDecimal quantity,
    @PositiveOrZero BigDecimal unitPrice,
    @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal discountPct) {

  public RegisterSalesOrderCommand.Item toCommand() {
    return new RegisterSalesOrderCommand.Item(
        new PackagingId(packagingId), quantity, unitPrice, discountPct);
  }
}
