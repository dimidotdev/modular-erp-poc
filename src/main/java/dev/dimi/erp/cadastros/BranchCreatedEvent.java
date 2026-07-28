package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Nova filial registrada no tenant via {@code POST /api/v1/cadastros/branches}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code BRANCH_CREATED}).
 *
 * @param branchId id da filial criada
 * @param tenantId tenant onde a filial foi criada
 * @param code código operacional ("001", "MTZ", etc)
 * @param name nome descritivo
 * @param isMain se a filial foi marcada como matriz no momento da criação
 * @param registeredBy administrador que disparou a criação
 * @param occurredAt timestamp do registro
 */
public record BranchCreatedEvent(
    BranchId branchId,
    TenantId tenantId,
    String code,
    String name,
    boolean isMain,
    UserId registeredBy,
    Instant occurredAt) {}
