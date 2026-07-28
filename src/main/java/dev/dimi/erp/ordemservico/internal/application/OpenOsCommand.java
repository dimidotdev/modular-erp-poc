package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.ordemservico.OsPriority;
import java.util.UUID;

/**
 * Comando interno do {@link OpenOsService}. Não é DTO público — o controller monta a partir do
 * payload validado.
 */
public record OpenOsCommand(
    BranchId branchId,
    PersonId customerId,
    UUID contactId,
    OsPriority priority,
    UUID assignedTo,
    UUID paymentConditionId,
    String equipmentDescription,
    String serialNumber,
    String problemReported,
    String notes) {}
