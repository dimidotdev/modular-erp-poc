package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.internal.application.OpenStockTransferCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de transferência. */
public record StockTransferItemApiRequest(
    @NotNull UUID packagingId, @NotNull @Positive BigDecimal quantity) {

  public OpenStockTransferCommand.Item toCommand() {
    return new OpenStockTransferCommand.Item(new PackagingId(packagingId), quantity);
  }
}
