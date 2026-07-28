package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.internal.application.PackagingSummary;
import dev.dimi.erp.produto.internal.domain.VariantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response de packaging. */
public record PackagingApiResponse(
    PackagingId id,
    VariantId variantId,
    String sku,
    String barcode,
    UUID commercialUnitId,
    UUID fiscalUnitId,
    BigDecimal conversionFactor,
    boolean isDefault,
    boolean active,
    BigDecimal weightKg,
    Instant createdAt,
    Instant updatedAt) {

  public static PackagingApiResponse from(PackagingSummary s) {
    return new PackagingApiResponse(
        s.id(),
        s.variantId(),
        s.sku(),
        s.barcode(),
        s.commercialUnitId(),
        s.fiscalUnitId(),
        s.conversionFactor(),
        s.isDefault(),
        s.active(),
        s.weightKg(),
        s.createdAt(),
        s.updatedAt());
  }
}
