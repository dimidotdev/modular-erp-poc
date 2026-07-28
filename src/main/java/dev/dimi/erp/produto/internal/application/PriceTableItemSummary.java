package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.internal.domain.PriceTableItem;
import dev.dimi.erp.produto.internal.domain.PriceTableItemId;
import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot read-only de {@link PriceTableItem}. */
public record PriceTableItemSummary(
    PriceTableItemId id,
    PriceTableId priceTableId,
    PackagingId packagingId,
    BigDecimal salePrice,
    BigDecimal discountMax,
    Instant createdAt,
    Instant updatedAt) {

  public static PriceTableItemSummary of(PriceTableItem item) {
    return new PriceTableItemSummary(
        item.id(),
        item.priceTableIdTyped(),
        item.packagingIdTyped(),
        item.getSalePrice(),
        item.getDiscountMax(),
        item.getCreatedAt(),
        item.getUpdatedAt());
  }
}
