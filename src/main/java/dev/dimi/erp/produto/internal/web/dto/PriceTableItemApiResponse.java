package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.internal.application.PriceTableItemSummary;
import dev.dimi.erp.produto.internal.domain.PriceTableItemId;
import java.math.BigDecimal;
import java.time.Instant;

/** Response de {@code price_table_item}. */
public record PriceTableItemApiResponse(
    PriceTableItemId id,
    PriceTableId priceTableId,
    PackagingId packagingId,
    BigDecimal salePrice,
    BigDecimal discountMax,
    Instant createdAt,
    Instant updatedAt) {

  public static PriceTableItemApiResponse from(PriceTableItemSummary s) {
    return new PriceTableItemApiResponse(
        s.id(),
        s.priceTableId(),
        s.packagingId(),
        s.salePrice(),
        s.discountMax(),
        s.createdAt(),
        s.updatedAt());
  }
}
