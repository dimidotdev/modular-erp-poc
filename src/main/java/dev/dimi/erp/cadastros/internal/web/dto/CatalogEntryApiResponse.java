package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.internal.application.CatalogEntrySummary;
import java.time.Instant;
import java.util.UUID;

/** Resposta HTTP plana para uma entrada de catálogo. */
public record CatalogEntryApiResponse(
    UUID id,
    String code,
    String name,
    boolean system,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static CatalogEntryApiResponse from(CatalogEntrySummary s) {
    return new CatalogEntryApiResponse(
        s.id(), s.code(), s.name(), s.system(), s.active(), s.createdAt(), s.updatedAt());
  }
}
