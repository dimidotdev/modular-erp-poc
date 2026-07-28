package dev.dimi.erp.cadastros.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body do {@code POST /api/v1/cadastros/persons/{id}/anonimizar} (RN-CAD-011/012).
 *
 * <p>{@code reason} é registrada no evento e no audit log (categoria LGPD) para compliance.
 * Operação é irreversível e exige step-up.
 */
public record AnonymizePersonApiRequest(@NotBlank @Size(max = 500) String reason) {}
