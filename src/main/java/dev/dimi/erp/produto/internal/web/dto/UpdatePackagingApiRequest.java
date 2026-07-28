package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.internal.application.UpdatePackagingCommand;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Payload do {@code PATCH /api/v1/produtos/packagings/{packagingId}}. */
public record UpdatePackagingApiRequest(
    @Size(max = 60) String barcode,
    UUID commercialUnitId,
    UUID fiscalUnitId,
    @Positive BigDecimal conversionFactor,
    @Positive BigDecimal weightKg) {

  public UpdatePackagingCommand toCommand(PackagingId packagingId, UserId actor) {
    return new UpdatePackagingCommand(
        packagingId, barcode, commercialUnitId, fiscalUnitId, conversionFactor, weightKg, actor);
  }
}
