package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.internal.application.RegisterQuotationResponseCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de resposta de cotação de um fornecedor. */
public record QuotationResponseLineApiRequest(
    int requestItemLine,
    UUID packagingId,
    @NotBlank @Size(max = 500) String description,
    @NotNull @Positive BigDecimal quantity,
    @NotNull @Positive BigDecimal unitPrice,
    @Size(max = 500) String notes) {

  public RegisterQuotationResponseCommand.Line toCommand() {
    return new RegisterQuotationResponseCommand.Line(
        requestItemLine,
        packagingId == null ? null : new PackagingId(packagingId),
        description,
        quantity,
        unitPrice,
        notes);
  }
}
