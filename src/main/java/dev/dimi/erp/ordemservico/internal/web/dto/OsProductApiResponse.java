package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representação REST de {@code service_order_product}. */
public record OsProductApiResponse(
    UUID id,
    UUID osId,
    int lineNumber,
    UUID packagingId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountValue,
    BigDecimal netValue,
    Instant consumedAt,
    UUID stockMovementId,
    Instant createdAt,
    Instant updatedAt) {

  public static OsProductApiResponse from(OsProduct p) {
    return new OsProductApiResponse(
        p.getId(),
        p.getOsId(),
        p.getLineNumber(),
        p.getPackagingId(),
        p.getQuantity(),
        p.getUnitPrice(),
        p.getDiscountValue(),
        p.getNetValue(),
        p.getConsumedAt(),
        p.getStockMovementId(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
