package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.application.VariantSummary;
import dev.dimi.erp.produto.internal.domain.VariantId;
import java.time.Instant;

/** Response de variant. */
public record VariantApiResponse(
    VariantId id,
    ProductId productId,
    String code,
    String name,
    boolean isDefault,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static VariantApiResponse from(VariantSummary s) {
    return new VariantApiResponse(
        s.id(),
        s.productId(),
        s.code(),
        s.name(),
        s.isDefault(),
        s.active(),
        s.createdAt(),
        s.updatedAt());
  }
}
