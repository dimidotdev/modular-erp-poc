package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.domain.Variant;
import dev.dimi.erp.produto.internal.domain.VariantId;
import java.time.Instant;

/** Snapshot read-only de {@link Variant}. */
public record VariantSummary(
    VariantId id,
    ProductId productId,
    String code,
    String name,
    boolean isDefault,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static VariantSummary of(Variant v) {
    return new VariantSummary(
        v.id(),
        v.productIdTyped(),
        v.getCode(),
        v.getName(),
        v.isDefault(),
        v.isActive(),
        v.getCreatedAt(),
        v.getUpdatedAt());
  }
}
