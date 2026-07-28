package dev.dimi.erp.estoque.internal.web.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Payload de {@code PATCH /api/v1/estoque/batches/{id}}. */
public record UpdateStockBatchApiRequest(
    LocalDate manufacturedOn, LocalDate expiresOn, @Size(max = 500) String notes) {}
