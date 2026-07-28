package dev.dimi.erp.contratos;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Contrato voltou de {@code SUSPENDED} para {@code ACTIVE}. O job de billing retoma geração de
 * ciclos a partir de {@code occurredAt}. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS}.
 * </ul>
 */
public record ContractResumedEvent(
    ContractId id, TenantId tenantId, UserId resumedBy, Instant occurredAt) {}
