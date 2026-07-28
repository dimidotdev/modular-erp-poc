package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Filial desativada (soft delete). Módulos consumidores (fiscal, movimentação) devem rejeitar novas
 * operações em filiais inativas.
 */
public record BranchDeactivatedEvent(
    BranchId branchId, TenantId tenantId, String reason, UserId actor, Instant occurredAt) {}
