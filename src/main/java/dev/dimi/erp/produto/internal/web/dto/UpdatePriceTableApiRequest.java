package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.internal.application.UpdatePriceTableCommand;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Payload de {@code PATCH /api/v1/produtos/price-tables/{id}}. */
public record UpdatePriceTableApiRequest(
    @Size(max = 120) String name, LocalDate validFrom, LocalDate validUntil) {

  public UpdatePriceTableCommand toCommand(PriceTableId priceTableId, UserId actor) {
    return new UpdatePriceTableCommand(priceTableId, name, validFrom, validUntil, actor);
  }
}
