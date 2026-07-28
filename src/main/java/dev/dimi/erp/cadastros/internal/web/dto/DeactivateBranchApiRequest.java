package dev.dimi.erp.cadastros.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body do {@code PATCH /api/v1/cadastros/branches/{id}/desativar}. */
public record DeactivateBranchApiRequest(@NotBlank @Size(max = 500) String reason) {}
