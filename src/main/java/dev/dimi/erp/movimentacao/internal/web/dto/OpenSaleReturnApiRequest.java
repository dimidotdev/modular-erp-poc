package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.internal.application.OpenSaleReturnCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/movimentacao/returns}. */
public record OpenSaleReturnApiRequest(
    @NotNull UUID saleId,
    @NotBlank @Size(max = 500) String reason,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<SaleReturnItemApiRequest> items) {

  public OpenSaleReturnCommand toCommand(UserId actor) {
    return new OpenSaleReturnCommand(
        new SaleId(saleId),
        reason,
        notes,
        items.stream().map(SaleReturnItemApiRequest::toCommand).toList(),
        actor);
  }
}
