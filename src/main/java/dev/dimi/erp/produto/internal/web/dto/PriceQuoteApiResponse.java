package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceLookupPort;
import dev.dimi.erp.produto.PriceTableId;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Response do lookup de preço autoritativo via {@code GET /api/v1/produtos/prices/quote}. */
public record PriceQuoteApiResponse(
    PackagingId packagingId,
    PriceTableId priceTableId,
    BranchId branchId,
    BigDecimal salePrice,
    BigDecimal discountMax,
    LocalDate validFrom,
    LocalDate validUntil) {

  public static PriceQuoteApiResponse from(PriceLookupPort.PriceQuote q) {
    return new PriceQuoteApiResponse(
        q.packagingId(),
        q.priceTableId(),
        q.branchId(),
        q.salePrice(),
        q.discountMax(),
        q.validFrom(),
        q.validUntil());
  }
}
