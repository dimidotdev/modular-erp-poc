package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.internal.application.RegisterPackagingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Payload do {@code POST /api/v1/produtos/{id}/variants/{variantId}/packagings}. */
public record RegisterPackagingApiRequest(
    @NotBlank @Size(max = 80) String sku,
    @Size(max = 60) String barcode,
    UUID commercialUnitId,
    UUID fiscalUnitId,
    @Positive BigDecimal conversionFactor,
    Boolean asDefault,
    @Positive BigDecimal weightKg) {

  public RegisterPackagingCommand toCommand(UUID variantId, UserId actor) {
    return new RegisterPackagingCommand(
        variantId,
        sku,
        barcode,
        commercialUnitId,
        fiscalUnitId,
        conversionFactor,
        Boolean.TRUE.equals(asDefault),
        weightKg,
        actor);
  }
}
