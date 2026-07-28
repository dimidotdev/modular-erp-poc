package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.Classification;
import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.application.ProductSummary;
import java.time.Instant;

/** Response de produto para os endpoints {@code /api/v1/produtos/**}. */
public record ProductApiResponse(
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

  public static ProductApiResponse from(ProductSummary s) {
    return new ProductApiResponse(
        s.id(),
        s.version(),
        s.skuRoot(),
        s.name(),
        s.description(),
        s.brandId(),
        s.classification(),
        s.ncm(),
        s.cest(),
        s.origin(),
        s.active(),
        s.createdAt(),
        s.updatedAt());
  }
}
