package dev.dimi.erp.estoque.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Payload de {@code POST /api/v1/estoque/locations}. */
public record CreateStockLocationApiRequest(
    @NotNull UUID branchId,
    @NotBlank @Size(max = 60) String code,
    @NotBlank @Size(max = 120) String name,
    Boolean asDefault) {}
