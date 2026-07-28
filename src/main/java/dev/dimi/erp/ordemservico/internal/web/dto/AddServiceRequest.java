package dev.dimi.erp.ordemservico.internal.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload do {@code POST /api/v1/ordens-servico/{id}/services} para adicionar linha de serviço à
 * OS. {@code serviceCode} é o código LC 116/2003 do município. {@code issqnRate} é percentual
 * (0..100).
 */
public record AddServiceRequest(
    @NotBlank @Size(max = 50) String serviceCode,
    @NotBlank @Size(max = 500) String description,
    @NotNull @Positive BigDecimal quantity,
    @NotNull @PositiveOrZero BigDecimal unitPrice,
    @PositiveOrZero BigDecimal discountValue,
    @NotNull @PositiveOrZero @DecimalMax("100") BigDecimal issqnRate) {}
