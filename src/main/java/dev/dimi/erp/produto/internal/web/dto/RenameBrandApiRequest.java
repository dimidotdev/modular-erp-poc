package dev.dimi.erp.produto.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload do {@code PATCH /api/v1/produtos/brands/{id}} — só rename suportado (code é imutável).
 */
public record RenameBrandApiRequest(@NotBlank @Size(max = 120) String name) {}
