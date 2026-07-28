package dev.dimi.erp.estoque.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de {@code POST /api/v1/estoque/movements/{id}/reverse}. */
public record ReverseStockApiRequest(@NotBlank @Size(max = 500) String reason) {}
