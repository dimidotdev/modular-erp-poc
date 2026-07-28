package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;

/**
 * Resultado do {@link TenantProvisioner#provision}.
 *
 * <p>{@code temporaryPassword} é a senha em CLARO do primeiro OWNER. <strong>Aparece uma única
 * vez</strong> nesta resposta — não é persistida nem logada. O OWNER deve trocar no primeiro login
 * ({@code mustChangePassword=true} já está setado no usuário).
 */
public record ProvisionTenantResult(
    TenantId tenantId,
    String tenantSlug,
    String schemaName,
    UserId ownerUserId,
    String ownerUsername,
    String temporaryPassword) {}
