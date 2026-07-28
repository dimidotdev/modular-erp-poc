package dev.dimi.erp.contratos;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Contrato suspenso (pausa temporária — diferente de cancelamento). Durante {@code SUSPENDED} o job
 * de billing não gera ciclos novos, mas o contrato pode voltar a {@code ACTIVE} via {@link
 * ContractResumedEvent}. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS} e {@code reason} no payload.
 * </ul>
 */
public record ContractSuspendedEvent(
    ContractId id, TenantId tenantId, String reason, UserId suspendedBy, Instant occurredAt) {}
