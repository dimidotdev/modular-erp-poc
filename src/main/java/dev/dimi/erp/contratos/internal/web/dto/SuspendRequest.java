package dev.dimi.erp.contratos.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload do {@code POST /{id}/suspend} — motivo obrigatório porque suspensão é evento auditável e
 * é appendado em {@code notes} do contrato para rastreabilidade.
 */
public record SuspendRequest(@NotBlank @Size(min = 5, max = 1000) String reason) {}
