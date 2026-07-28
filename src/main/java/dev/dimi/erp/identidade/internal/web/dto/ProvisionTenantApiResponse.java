package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.application.ProvisionTenantResult;
import dev.dimi.erp.shared.multitenancy.TenantId;

/**
 * Response do {@code POST /api/v1/platform/tenants}.
 *
 * <p>{@code temporaryPassword} é apresentada uma única vez para o operador entregar ao OWNER. O
 * OWNER deve trocar no primeiro login ({@code mustChangePassword=true}).
 */
public record ProvisionTenantApiResponse(
    TenantId tenantId,
    String slug,
    String schemaName,
    UserId ownerUserId,
    String ownerUsername,
    String temporaryPassword) {

  public static ProvisionTenantApiResponse from(ProvisionTenantResult r) {
    return new ProvisionTenantApiResponse(
        r.tenantId(),
        r.tenantSlug(),
        r.schemaName(),
        r.ownerUserId(),
        r.ownerUsername(),
        r.temporaryPassword());
  }
}
