package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.AmendmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload do {@code POST /{id}/amendments} para criar aditivo em {@code DRAFT}. A efetivação
 * (aplicação do efeito no contrato) acontece em {@code POST /amendments/{id}/effective} separado.
 *
 * <p>{@code newEndDate}/{@code newBaseValue}/{@code payload} são opcionais aqui — o {@code type}
 * determina quais campos serão usados pelo {@code AmendContractService} na efetivação ({@code
 * EXTENSION} usa {@code newEndDate}; {@code VALUE} usa {@code newBaseValue}; {@code OTHER} só
 * registra {@code description}).
 */
public record CreateAmendmentRequest(
    @NotNull AmendmentType type,
    @NotNull LocalDate effectiveFrom,
    @NotBlank @Size(min = 5, max = 2000) String description,
    LocalDate newEndDate,
    BigDecimal newBaseValue,
    String payload) {}
