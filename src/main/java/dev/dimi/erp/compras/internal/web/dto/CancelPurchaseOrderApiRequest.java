package dev.dimi.erp.compras.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de {@code POST /api/v1/compras/orders/{id}/cancel}. */
public record CancelPurchaseOrderApiRequest(@NotBlank @Size(max = 2000) String reason) {}
