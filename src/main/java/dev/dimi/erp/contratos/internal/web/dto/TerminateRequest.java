package dev.dimi.erp.contratos.internal.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload do {@code POST /{id}/terminate}. {@code earlyTermination=true} dispara multa em {@code
 * cancellationFeeValue} (RN-CTR-005) — listener financeiro do slice 10.2 cria MVF de multa.
 */
public record TerminateRequest(
    @NotNull LocalDate terminationDate,
    @NotBlank @Size(min = 5, max = 1000) String reason,
    boolean earlyTermination) {}
