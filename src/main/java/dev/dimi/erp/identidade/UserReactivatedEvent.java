package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Usuário SUSPENDED voltou a ACTIVE via {@code PATCH /api/v1/admin/users/{id}/reativar}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code ADMIN}, tipo {@code USER_REACTIVATED}).
 * DEACTIVATED é status final — não dispara este evento (vide {@code UserManagementService}).
 */
public record UserReactivatedEvent(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    UserId reactivatedBy,
    Instant occurredAt) {}
