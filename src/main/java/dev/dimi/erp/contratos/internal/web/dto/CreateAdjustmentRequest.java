package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.AdjustmentIndex;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload do {@code POST /{id}/adjustments} para aplicar reajuste por índice. {@code percentage} é
 * negociável (-50..+500 — guard-rail validado no service, não aqui — typo de operador), permite
 * negativo (rebate) e positivo (correção).
 */
public record CreateAdjustmentRequest(
    @NotNull AdjustmentIndex index,
    @NotNull BigDecimal percentage,
    @NotNull LocalDate appliedOn,
    @Size(max = 2000) String notes) {}
