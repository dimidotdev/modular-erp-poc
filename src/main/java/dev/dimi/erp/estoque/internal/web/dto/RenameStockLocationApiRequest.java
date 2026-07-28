package dev.dimi.erp.estoque.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de {@code PATCH /api/v1/estoque/locations/{id}}. */
public record RenameStockLocationApiRequest(@NotBlank @Size(max = 120) String name) {}
