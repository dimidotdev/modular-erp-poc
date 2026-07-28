package dev.dimi.erp.cadastros.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request body do {@code POST /api/v1/cadastros/catalogs/{type}}. */
public record CatalogEntryCreateApiRequest(
    @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Z0-9_\\-]+$") String code,
    @NotBlank @Size(max = 120) String name) {}
