package dev.dimi.erp.cadastros.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Request body do {@code POST /api/v1/cadastros/employees/{id}/terminate}. */
public record TerminateEmployeeApiRequest(
    @NotNull @PastOrPresent LocalDate terminatedAt, @NotBlank @Size(max = 500) String reason) {}
