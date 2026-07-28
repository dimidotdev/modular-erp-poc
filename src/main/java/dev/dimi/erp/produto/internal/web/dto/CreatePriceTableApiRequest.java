package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.internal.application.RegisterPriceTableCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Payload de {@code POST /api/v1/produtos/price-tables}. */
public record CreatePriceTableApiRequest(
    @NotBlank @Size(max = 120) String name,
    UUID branchId,
    @NotNull LocalDate validFrom,
    LocalDate validUntil) {

  public RegisterPriceTableCommand toCommand(UserId actor) {
    return new RegisterPriceTableCommand(
        name, branchId == null ? null : new BranchId(branchId), validFrom, validUntil, actor);
  }
}
