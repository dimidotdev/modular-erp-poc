package dev.dimi.erp.estoque.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload de {@code POST /api/v1/estoque/adjustments}. Delta positivo = entrada, negativo = saída.
 * Reason obrigatório (RN-EST-010).
 */
public record AdjustStockApiRequest(
    @NotNull UUID packagingId,
    @NotNull UUID branchId,
    @NotNull UUID locationId,
    UUID batchId,
    @NotNull BigDecimal delta,
    @NotBlank @Size(max = 500) String reason) {}
