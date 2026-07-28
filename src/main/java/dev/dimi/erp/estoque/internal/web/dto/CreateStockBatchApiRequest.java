package dev.dimi.erp.estoque.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Payload de {@code POST /api/v1/estoque/batches}. */
public record CreateStockBatchApiRequest(
    @NotNull UUID packagingId,
    @NotBlank @Size(max = 60) String code,
    LocalDate manufacturedOn,
    LocalDate expiresOn,
    @Size(max = 500) String notes) {}
