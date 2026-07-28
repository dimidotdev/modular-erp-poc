package dev.dimi.erp.estoque.internal.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Payload de {@code POST /api/v1/estoque/transfers}. */
public record TransferStockApiRequest(
    @NotNull UUID packagingId,
    @NotNull UUID sourceBranchId,
    @NotNull UUID sourceLocationId,
    @NotNull UUID destBranchId,
    @NotNull UUID destLocationId,
    UUID batchId,
    @NotNull @Positive BigDecimal quantity,
    @Size(max = 500) String reason) {}
