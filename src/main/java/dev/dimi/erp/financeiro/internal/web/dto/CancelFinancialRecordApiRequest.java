package dev.dimi.erp.financeiro.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de {@code POST /api/v1/financeiro/records/{id}/cancel}. */
public record CancelFinancialRecordApiRequest(@NotBlank @Size(max = 2000) String reason) {}
