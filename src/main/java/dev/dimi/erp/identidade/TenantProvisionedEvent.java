package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Tenant novo provisionado com sucesso pelo {@code POST /api/v1/platform/tenants}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code PLATFORM_ADMIN}, tipo {@code
 * TENANT_PROVISIONED}). Não inclui a senha temporária do OWNER — essa só vai pro response da API,
 * jamais ao audit log.
 */
public record TenantProvisionedEvent(
    TenantId tenantId,
    String tenantSlug,
    String schemaName,
    UserId ownerUserId,
    String ownerUsername,
    Instant occurredAt) {}
