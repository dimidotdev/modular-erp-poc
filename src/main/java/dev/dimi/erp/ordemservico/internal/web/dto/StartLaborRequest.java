package dev.dimi.erp.ordemservico.internal.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Payload do {@code POST /api/v1/ordens-servico/{id}/labors} para iniciar (clock-in) apontamento de
 * mão-de-obra. {@code description} e {@code relatedServiceLine} são opcionais.
 */
public record StartLaborRequest(
    @NotNull UUID employeeId,
    @Size(max = 1000) String description,
    boolean isBillable,
    Integer relatedServiceLine) {}
