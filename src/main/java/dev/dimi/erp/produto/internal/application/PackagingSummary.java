package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.internal.domain.Packaging;
import dev.dimi.erp.produto.internal.domain.VariantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Snapshot read-only de {@link Packaging}. */
public record PackagingSummary(
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

  public static PackagingSummary of(Packaging p) {
    return new PackagingSummary(
        p.id(),
        p.variantIdTyped(),
        p.getSku(),
        p.getBarcode(),
        p.getCommercialUnitId(),
        p.getFiscalUnitId(),
        p.getConversionFactor(),
        p.isDefault(),
        p.isActive(),
        p.getWeightKg(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
