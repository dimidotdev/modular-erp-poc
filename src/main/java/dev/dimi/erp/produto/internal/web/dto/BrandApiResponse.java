package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.internal.application.BrandSummary;
import java.time.Instant;

/** Response de marca para os endpoints {@code /produtos/brands/**}. */
public record BrandApiResponse(
    BrandId id, String code, String name, boolean active, Instant createdAt, Instant updatedAt) {

  public static BrandApiResponse from(BrandSummary s) {
    return new BrandApiResponse(
        s.id(), s.code(), s.name(), s.active(), s.createdAt(), s.updatedAt());
  }
}
