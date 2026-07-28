package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.OsPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Payload do {@code POST /api/v1/ordens-servico} para abrir nova OS. */
public record OpenOsRequest(
    @NotNull UUID branchId,
    @NotNull UUID customerId,
    UUID contactId,
    OsPriority priority,
    UUID assignedTo,
    UUID paymentConditionId,
    @Size(max = 500) String equipmentDescription,
    @Size(max = 100) String serialNumber,
    @Size(max = 2000) String problemReported,
    @Size(max = 2000) String notes) {}
