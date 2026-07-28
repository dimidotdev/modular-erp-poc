package dev.dimi.erp.ordemservico.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload do {@code POST /api/v1/ordens-servico/{id}/billings/{billingId}/cancel}. Motivo é
 * obrigatório porque cancelamento é evento auditável e (futuro slice 8.6) será propagado para
 * cancelamento fiscal SEFAZ — que também exige justificativa.
 */
public record CancelBillingRequest(@NotBlank @Size(min = 5, max = 1000) String reason) {}
