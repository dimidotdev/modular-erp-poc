package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.Recurrence;
import dev.dimi.erp.contratos.internal.domain.Contract;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representação REST de {@code contract}. Itens não vêm acoplados — cliente busca em {@code GET
 * /{id}/items} quando precisar (mantém payload do GET enxuto e cacheável).
 */
public record ContractApiResponse(
    UUID id,
    long version,
    long number,
    UUID branchId,
    UUID customerId,
    UUID contactId,
    String title,
    String description,
    ContractStatus status,
    LocalDate startDate,
    LocalDate endDate,
    boolean autoRenew,
    Integer renewalPeriodMonths,
    Recurrence recurrence,
    int billingDay,
    UUID paymentConditionId,
    BigDecimal baseValue,
    LocalDate lastAdjustmentDate,
    LocalDate nextAdjustmentDate,
    UUID costCenterId,
    String cancellationClause,
    BigDecimal cancellationFeeValue,
    Integer cancellationNoticeDays,
    String notes,
    UUID createdBy,
    Instant activatedAt,
    Instant suspendedAt,
    Instant terminatedAt,
    String terminationReason,
    Instant createdAt,
    Instant updatedAt) {

  public static ContractApiResponse from(Contract c) {
    return new ContractApiResponse(
        c.getId(),
        c.getVersion(),
        c.getNumber(),
        c.getBranchId(),
        c.getCustomerId(),
        c.getContactId(),
        c.getTitle(),
        c.getDescription(),
        c.getStatus(),
        c.getStartDate(),
        c.getEndDate(),
        c.isAutoRenew(),
        c.getRenewalPeriodMonths(),
        c.getRecurrence(),
        c.getBillingDay(),
        c.getPaymentConditionId(),
        c.getBaseValue(),
        c.getLastAdjustmentDate(),
        c.getNextAdjustmentDate(),
        c.getCostCenterId(),
        c.getCancellationClause(),
        c.getCancellationFeeValue(),
        c.getCancellationNoticeDays(),
        c.getNotes(),
        c.getCreatedBy(),
        c.getActivatedAt(),
        c.getSuspendedAt(),
        c.getTerminatedAt(),
        c.getTerminationReason(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }
}
