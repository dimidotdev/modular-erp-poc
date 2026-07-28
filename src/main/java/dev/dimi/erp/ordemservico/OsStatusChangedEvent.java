package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Transição de status executada com sucesso (já validada contra {@code os_workflow_transition}).
 * Consumidores conhecidos: {@code auditoria}; 9.2 reage a transição para {@code BILLED} disparando
 * fluxo de faturamento.
 *
 * <p>Carrega códigos {@code fromCode}/{@code toCode} (e não só IDs) para o consumer não precisar
 * lookup reverso.
 */
public record OsStatusChangedEvent(
    ServiceOrderId osId,
    TenantId tenantId,
    WorkflowStatusId fromStatusId,
    String fromCode,
    WorkflowStatusId toStatusId,
    String toCode,
    UserId changedBy,
    String notes,
    Instant occurredAt) {}
