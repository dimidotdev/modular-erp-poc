package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.internal.application.SaleItemSummary;
import dev.dimi.erp.movimentacao.internal.domain.SaleItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de linha de venda. */
public record SaleItemApiResponse(
    SaleItemId id,
    SaleId saleId,
    int lineNumber,
    PackagingId packagingId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountValue,
    BigDecimal netValue,
    String cfop) {

  public static SaleItemApiResponse from(SaleItemSummary s) {
    return new SaleItemApiResponse(
        s.id(),
        s.saleId(),
        s.lineNumber(),
        s.packagingId(),
        s.quantity(),
        s.unitPrice(),
        s.discountValue(),
        s.netValue(),
        s.cfop());
  }
}
