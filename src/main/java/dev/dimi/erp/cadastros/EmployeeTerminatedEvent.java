package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Funcionário desligado via {@code POST /api/v1/cadastros/employees/{id}/terminate}.
 *
 * <p>Consumido por {@code auditoria}; opcionalmente por {@code identidade} para desativar {@code
 * user_account} vinculado à pessoa.
 *
 * @param employeeId id do vínculo encerrado
 * @param personId pessoa desligada
 * @param branchId filial onde o vínculo terminou
 * @param tenantId tenant da operação
 * @param terminatedAt data efetiva do desligamento
 * @param reason motivo informado pelo administrador
 * @param terminatedBy administrador que registrou o desligamento
 * @param occurredAt timestamp do registro
 */
public record EmployeeTerminatedEvent(
    EmployeeId employeeId,
    PersonId personId,
    BranchId branchId,
    TenantId tenantId,
    LocalDate terminatedAt,
    String reason,
    UserId terminatedBy,
    Instant occurredAt) {}
