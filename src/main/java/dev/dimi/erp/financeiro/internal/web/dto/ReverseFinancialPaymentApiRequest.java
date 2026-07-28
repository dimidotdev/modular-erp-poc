package dev.dimi.erp.financeiro.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de POST /api/v1/financeiro/payments/{id}/reverse. */
public record ReverseFinancialPaymentApiRequest(@NotBlank @Size(max = 2000) String reason) {}
