package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.internal.domain.BaseCatalog;
import java.time.Instant;
import java.util.UUID;

/** Projeção plana de qualquer {@link BaseCatalog} para resposta de service. */
public record CatalogEntrySummary(
    UUID id,
    String code,
    String name,
    boolean system,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static CatalogEntrySummary of(BaseCatalog c) {
    return new CatalogEntrySummary(
        c.getId(),
        c.getCode(),
        c.getName(),
        c.isSystem(),
        c.isActive(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
