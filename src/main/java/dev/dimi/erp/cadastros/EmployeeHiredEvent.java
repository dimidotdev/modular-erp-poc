package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Funcionário contratado em uma filial via {@code POST /api/v1/cadastros/employees}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code EMPLOYEE_HIRED}).
 *
 * @param employeeId id do vínculo {@code person + branch}
 * @param personId pessoa contratada
 * @param branchId filial onde foi contratada
 * @param tenantId tenant da operação
 * @param hiredAt data de admissão
 * @param hiredBy administrador que registrou a contratação
 * @param occurredAt timestamp do registro
 */
public record EmployeeHiredEvent(
    EmployeeId employeeId,
    PersonId personId,
    BranchId branchId,
    TenantId tenantId,
    LocalDate hiredAt,
    UserId hiredBy,
    Instant occurredAt) {}
