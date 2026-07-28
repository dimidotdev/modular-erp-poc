package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.internal.domain.SaleItemId;
import java.math.BigDecimal;
import java.util.List;

/**
 * Comando de abertura de devolução. Cada item referencia um {@code sale_item} específico e carrega
 * as 3 quantidades (RN-MOV-014). Soma das 3 ≤ {@code sale_item.quantity} (validado no service).
 */
public record OpenSaleReturnCommand(
    SaleId saleId, String reason, String notes, List<Item> items, UserId actor) {

  public record Item(
      SaleItemId saleItemId,
      BigDecimal quantityReturned,
      BigDecimal quantityLost,
      BigDecimal quantityUsed) {}
}
