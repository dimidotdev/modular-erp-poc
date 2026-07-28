package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Set;

/**
 * Atualização parcial de funcionário (cargo, departamento, salário, notas).
 *
 * <p>Consumido por {@code auditoria}. Campos imutáveis ({@code personId}, {@code branchId}, {@code
 * hiredAt}) nunca aparecem em {@code changedFields}.
 *
 * @param employeeId id do vínculo
 * @param tenantId tenant da operação
 * @param changedFields nomes dos campos modificados (ex: {@code jobTitleId}, {@code salary})
 * @param updatedBy administrador que disparou a atualização
 * @param occurredAt timestamp do registro
 */
public record EmployeeUpdatedEvent(
    EmployeeId employeeId,
    TenantId tenantId,
    Set<String> changedFields,
    UserId updatedBy,
    Instant occurredAt) {}
