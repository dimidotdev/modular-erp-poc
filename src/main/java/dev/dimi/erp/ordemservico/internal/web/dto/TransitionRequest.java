package dev.dimi.erp.ordemservico.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de transição genérica: muda OS para o status identificado por {@code targetCode}. */
public record TransitionRequest(
    @NotBlank @Size(max = 64) String targetCode, @Size(max = 1000) String notes) {}
