package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.internal.domain.CatalogBrand;
import java.time.Instant;

/** Snapshot read-model de uma {@link CatalogBrand} para uso por DTOs/web. */
public record BrandSummary(
    BrandId id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {

  public static BrandSummary of(CatalogBrand b) {
    return new BrandSummary(
        b.id(), b.getCode(), b.getName(), b.isActive(), b.getCreatedAt(), b.getUpdatedAt());
  }
}
