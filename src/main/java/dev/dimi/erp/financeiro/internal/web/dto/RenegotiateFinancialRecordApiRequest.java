package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.internal.application.RenegotiateFinancialRecordCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload de {@code POST /api/v1/financeiro/records/{id}/renegotiate}. */
public record RenegotiateFinancialRecordApiRequest(
    @NotNull @Positive BigDecimal newTotalValue,
    @NotBlank @Size(max = 2000) String reason,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<NewInstallmentLineApiRequest> newInstallments) {

  public RenegotiateFinancialRecordCommand toCommand(UUID originalRecordId, UserId actor) {
    return new RenegotiateFinancialRecordCommand(
        new FinancialRecordId(originalRecordId),
        newTotalValue,
        reason,
        notes,
        newInstallments.stream().map(NewInstallmentLineApiRequest::toCommand).toList(),
        actor);
  }
}
