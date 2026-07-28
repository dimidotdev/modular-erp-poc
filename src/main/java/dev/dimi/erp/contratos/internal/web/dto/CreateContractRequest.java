package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.ItemType;
import dev.dimi.erp.contratos.Recurrence;
import dev.dimi.erp.contratos.internal.application.CreateContractCommand.ContractItemSpec;
import dev.dimi.erp.contratos.internal.domain.ContractItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload do {@code POST /api/v1/contratos} para criar contrato em {@code DRAFT}.
 *
 * <p>{@code items} é obrigatório e {@code @NotEmpty} — contrato sem itens não pode nem ser ativado
 * (RN-CTR-002), então recusar já no POST evita criar lixo. Cada item é {@code @Valid} via cascata.
 *
 * <p>{@code billingDay} aceita 1..31; ajuste para meses curtos é responsabilidade do job de billing
 * (slice 10.2).
 */
public record CreateContractRequest(
    @NotNull UUID branchId,
    @NotNull UUID customerId,
    UUID contactId,
    @NotBlank @Size(max = 300) String title,
    @Size(max = 5000) String description,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    @NotNull Recurrence recurrence,
    @Min(1) @Max(31) int billingDay,
    @NotNull @DecimalMin("0") BigDecimal baseValue,
    UUID paymentConditionId,
    UUID costCenterId,
    boolean autoRenew,
    @Min(1) Integer renewalPeriodMonths,
    @Size(max = 5000) String cancellationClause,
    @PositiveOrZero BigDecimal cancellationFeeValue,
    @Min(0) Integer cancellationNoticeDays,
    @Size(max = 5000) String notes,
    @NotNull @NotEmpty @Valid List<ContractItemRequest> items) {

  /**
   * Item do contrato. {@code type=PRODUCT} exige {@code packagingId}; {@code type=SERVICE} exige
   * {@code serviceCode} — checagem mutuamente exclusiva fica no service (espelha a constraint
   * {@code ck_contract_item_type_payload} do banco). Aqui validamos só formato individual.
   */
  public record ContractItemRequest(
      @NotNull ItemType type,
      UUID packagingId,
      @Size(max = 50) String serviceCode,
      @NotBlank @Size(max = 500) String description,
      @NotNull @Positive BigDecimal quantity,
      @NotNull @PositiveOrZero BigDecimal unitPrice,
      @PositiveOrZero BigDecimal discountValue) {

    /** Mapeia para o spec interno do command, traduzindo o enum público para o interno. */
    public ContractItemSpec toSpec() {
      return new ContractItemSpec(
          toInternal(type),
          packagingId,
          serviceCode,
          description,
          quantity,
          unitPrice,
          discountValue);
    }
  }

  private static ContractItem.ItemType toInternal(ItemType pub) {
    return switch (pub) {
      case PRODUCT -> ContractItem.ItemType.PRODUCT;
      case SERVICE -> ContractItem.ItemType.SERVICE;
    };
  }
}
