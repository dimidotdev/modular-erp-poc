package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.OsPriority;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representação REST de {@code service_order}. */
public record ServiceOrderApiResponse(
    UUID id,
    long version,
    long number,
    UUID branchId,
    UUID customerId,
    UUID contactId,
    UUID statusId,
    OsPriority priority,
    Instant openedAt,
    Instant startedAt,
    Instant finishedAt,
    Instant billedAt,
    Instant closedAt,
    Instant cancelledAt,
    String cancellationReason,
    UUID paymentConditionId,
    String equipmentDescription,
    String serialNumber,
    String problemReported,
    String diagnosis,
    String solution,
    BigDecimal totalProducts,
    BigDecimal totalServices,
    BigDecimal totalDiscount,
    BigDecimal total,
    String notes,
    UUID createdBy,
    UUID assignedTo,
    Instant createdAt,
    Instant updatedAt) {

  public static ServiceOrderApiResponse from(ServiceOrder os) {
    return new ServiceOrderApiResponse(
        os.getId(),
        os.getVersion(),
        os.getNumber(),
        os.getBranchId(),
        os.getCustomerId(),
        os.getContactId(),
        os.getStatusId(),
        os.getPriority(),
        os.getOpenedAt(),
        os.getStartedAt(),
        os.getFinishedAt(),
        os.getBilledAt(),
        os.getClosedAt(),
        os.getCancelledAt(),
        os.getCancellationReason(),
        os.getPaymentConditionId(),
        os.getEquipmentDescription(),
        os.getSerialNumber(),
        os.getProblemReported(),
        os.getDiagnosis(),
        os.getSolution(),
        os.getTotalProducts(),
        os.getTotalServices(),
        os.getTotalDiscount(),
        os.getTotal(),
        os.getNotes(),
        os.getCreatedBy(),
        os.getAssignedTo(),
        os.getCreatedAt(),
        os.getUpdatedAt());
  }
}
