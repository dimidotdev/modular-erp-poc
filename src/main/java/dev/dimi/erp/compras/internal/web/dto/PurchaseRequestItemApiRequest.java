package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.internal.application.RegisterPurchaseRequestCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de SC em {@code POST /api/v1/compras/requests}. */
public record PurchaseRequestItemApiRequest(
    UUID packagingId,
    @NotBlank @Size(max = 500) String description,
    @NotNull @Positive BigDecimal quantity,
    @PositiveOrZero BigDecimal estimatedUnitCost,
    @Size(max = 500) String notes) {

  public RegisterPurchaseRequestCommand.Item toCommand() {
    return new RegisterPurchaseRequestCommand.Item(
        packagingId == null ? null : new PackagingId(packagingId),
        description,
        quantity,
        estimatedUnitCost,
        notes);
  }
}
