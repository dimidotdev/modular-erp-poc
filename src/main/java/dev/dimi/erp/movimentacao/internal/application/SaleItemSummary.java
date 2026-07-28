package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.internal.domain.SaleItem;
import dev.dimi.erp.movimentacao.internal.domain.SaleItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Snapshot read-only de linha de venda. */
public record SaleItemSummary(
    SaleItemId id,
    SaleId saleId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountValue,
    BigDecimal netValue,
    String cfop) {

  public static SaleItemSummary of(SaleItem i) {
    return new SaleItemSummary(
        i.id(),
        i.saleIdTyped(),
        i.getLineNumber(),
        i.packagingIdTyped(),
        i.getQuantity(),
        i.getUnitPrice(),
        i.getDiscountValue(),
        i.getNetValue(),
        i.getCfop());
  }
}
