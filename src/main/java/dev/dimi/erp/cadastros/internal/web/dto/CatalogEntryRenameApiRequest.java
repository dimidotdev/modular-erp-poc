package dev.dimi.erp.cadastros.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body do {@code PATCH /api/v1/cadastros/catalogs/{type}/{id}}. */
public record CatalogEntryRenameApiRequest(@NotBlank @Size(max = 120) String name) {}
