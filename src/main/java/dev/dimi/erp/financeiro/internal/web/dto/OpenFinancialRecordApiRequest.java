package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.internal.application.OpenFinancialRecordCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Payload de {@code POST /api/v1/financeiro/records}. {@code originType=MANUAL} =&gt; {@code
 * originId} deve ser nulo; demais origens carregam o id do agregado fonte.
 */
public record OpenFinancialRecordApiRequest(
    @NotNull UUID branchId,
    @NotNull FinancialDirection direction,
    @NotNull UUID counterpartyId,
    @Size(max = 100) String documentNumber,
    @NotNull @Positive BigDecimal totalValue,
    @NotNull FinancialOriginType originType,
    UUID originId,
    @Size(max = 2000) String notes,
    @Size(max = 200) String idempotencyKey,
    @NotEmpty @Valid List<InstallmentLineApiRequest> installments,
    @NotEmpty @Valid List<ApportionmentLineApiRequest> apportionments) {

  public OpenFinancialRecordCommand toCommand(UserId actor) {
    return new OpenFinancialRecordCommand(
        new BranchId(branchId),
        direction,
        new PersonId(counterpartyId),
        documentNumber,
        totalValue,
        originType,
        originId,
        notes,
        idempotencyKey,
        installments.stream().map(InstallmentLineApiRequest::toCommand).toList(),
        apportionments.stream().map(ApportionmentLineApiRequest::toCommand).toList(),
        actor);
  }
}
