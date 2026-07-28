package dev.dimi.erp.ordemservico;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Apontamento de mão-de-obra iniciado (clock-in). Consumidores conhecidos: {@code auditoria}; no
 * futuro relatórios de produtividade por técnico podem consumir.
 *
 * <p>Evento gordinho — leva {@code employeeId} e {@code startedAt} prontos.
 */
public record LaborAppointedEvent(
    OsLaborId laborId,
    ServiceOrderId osId,
    TenantId tenantId,
    EmployeeId employeeId,
    Instant startedAt,
    UserId actor,
    Instant occurredAt) {}
