package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.contratos.Recurrence;
import dev.dimi.erp.contratos.internal.domain.ContractItem.ItemType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comando interno do {@link CreateContractService}. Não é DTO público — o controller monta a partir
 * do payload validado.
 *
 * <p>{@code items} é a lista de linhas que vai virar {@code contract_item} — cada uma discriminada
 * por {@link ItemType} ({@code PRODUCT} → {@code packagingId}; {@code SERVICE} → {@code
 * serviceCode}). {@code lineNumber} é atribuído pelo service na ordem do array (índice + 1).
 */
public record CreateContractCommand(
    BranchId branchId,
    PersonId customerId,
    UUID contactId,
    String title,
    String description,
    LocalDate startDate,
    LocalDate endDate,
    Recurrence recurrence,
    int billingDay,
    BigDecimal baseValue,
    UUID paymentConditionId,
    UUID costCenterId,
    boolean autoRenew,
    Integer renewalPeriodMonths,
    String cancellationClause,
    BigDecimal cancellationFeeValue,
    Integer cancellationNoticeDays,
    String notes,
    List<ContractItemSpec> items) {

  /**
   * Especificação de uma linha do contrato. Mutuamente exclusivo: {@code PRODUCT} exige {@code
   * packagingId} e ignora {@code serviceCode}; {@code SERVICE} exige {@code serviceCode} e ignora
   * {@code packagingId} — espelha a constraint {@code ck_contract_item_type_payload}.
   */
  public record ContractItemSpec(
      ItemType type,
      UUID packagingId,
      String serviceCode,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue) {}
}
