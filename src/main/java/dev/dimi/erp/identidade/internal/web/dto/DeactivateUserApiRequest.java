package dev.dimi.erp.identidade.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body do {@code PATCH /api/v1/admin/users/{id}/desativar}.
 *
 * <p>{@code reason} é exigido para audit — desativação sem motivo dificulta investigação futura
 * (ex: ex-funcionário vs comprometimento de conta).
 */
public record DeactivateUserApiRequest(@NotBlank @Size(max = 500) String reason) {}
