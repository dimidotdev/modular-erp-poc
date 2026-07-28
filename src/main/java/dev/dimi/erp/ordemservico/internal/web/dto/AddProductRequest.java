package dev.dimi.erp.ordemservico.internal.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload do {@code POST /api/v1/ordens-servico/{id}/products} para adicionar linha de produto à
 * OS. {@code discountValue} é opcional — default zero no domínio quando {@code null}.
 */
public record AddProductRequest(
    @NotNull UUID packagingId,
    @NotNull @Positive BigDecimal quantity,
    @NotNull @PositiveOrZero BigDecimal unitPrice,
    @PositiveOrZero BigDecimal discountValue) {}
