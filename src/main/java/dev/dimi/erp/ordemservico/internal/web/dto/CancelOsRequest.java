package dev.dimi.erp.ordemservico.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload do {@code POST /{id}/cancel} — motivo obrigatório porque cancelamento é evento auditável.
 */
public record CancelOsRequest(@NotBlank @Size(min = 5, max = 1000) String reason) {}
