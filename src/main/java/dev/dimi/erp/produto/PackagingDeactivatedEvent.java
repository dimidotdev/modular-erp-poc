package dev.dimi.erp.produto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Embalagem desativada (soft delete). Movimentos de estoque novos com esse {@code PackagingId}
 * devem ser bloqueados pelos módulos a jusante. Saldo existente não é apagado.
 *
 * @param packagingId embalagem desativada
 * @param tenantId tenant da operação
 * @param deactivatedBy usuário que disparou
 * @param occurredAt timestamp da desativação
 */
public record PackagingDeactivatedEvent(
    PackagingId packagingId, TenantId tenantId, UserId deactivatedBy, Instant occurredAt) {}
