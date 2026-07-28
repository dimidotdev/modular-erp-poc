package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.internal.application.OpenSaleReturnCommand;
import dev.dimi.erp.movimentacao.internal.domain.SaleItemId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de devolução com 3 quantidades (RN-MOV-014). */
public record SaleReturnItemApiRequest(
    @NotNull UUID saleItemId,
    @PositiveOrZero BigDecimal quantityReturned,
    @PositiveOrZero BigDecimal quantityLost,
    @PositiveOrZero BigDecimal quantityUsed) {

  public OpenSaleReturnCommand.Item toCommand() {
    return new OpenSaleReturnCommand.Item(
        new SaleItemId(saleItemId), quantityReturned, quantityLost, quantityUsed);
  }
}
