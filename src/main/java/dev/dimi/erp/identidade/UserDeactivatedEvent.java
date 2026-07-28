package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Usuário desativado por admin via {@code PATCH /api/v1/admin/users/{id}/desativar}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code ADMIN}, tipo {@code USER_DEACTIVATED}). Não
 * sinaliza "DEACTIVATED final" vs "SUSPENDED" — só captura a operação administrativa, com {@code
 * reason} para ajudar investigação posterior.
 */
public record UserDeactivatedEvent(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    String reason,
    UserId deactivatedBy,
    Instant occurredAt) {}
