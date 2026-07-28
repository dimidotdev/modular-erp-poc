package dev.dimi.erp.cadastros.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body do {@code POST /api/v1/cadastros/persons/{id}/modalidades/{modalidade}/desativar}.
 */
public record DeactivateModalityApiRequest(@NotBlank @Size(max = 500) String reason) {}
