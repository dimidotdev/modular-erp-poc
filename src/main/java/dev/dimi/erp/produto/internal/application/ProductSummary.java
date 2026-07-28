package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.Classification;
import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.domain.Product;
import java.time.Instant;

/** Snapshot read-only de {@link Product}. */
public record ProductSummary(
    ProductId id,
    long version,
    String skuRoot,
    String name,
    String description,
    BrandId brandId,
    Classification classification,
    String ncm,
    String cest,
    String origin,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static ProductSummary of(Product p) {
    return new ProductSummary(
        p.id(),
        p.getVersion(),
        p.getSkuRoot(),
        p.getName(),
        p.getDescription(),
        p.getBrandId() == null ? null : new BrandId(p.getBrandId()),
        p.getClassification(),
        p.getNcm(),
        p.getCest(),
        p.getOrigin(),
        p.isActive(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
