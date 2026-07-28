package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/** Filial previamente inativa volta a aceitar operações. */
public record BranchReactivatedEvent(
    BranchId branchId, TenantId tenantId, UserId actor, Instant occurredAt) {}
