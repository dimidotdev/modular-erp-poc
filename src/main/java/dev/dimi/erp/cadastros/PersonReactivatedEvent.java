package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Pessoa reativada via {@code PATCH /api/v1/cadastros/pessoas/{id}/reativar}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code PERSON_REACTIVATED}).
 */
public record PersonReactivatedEvent(
    PersonId personId, TenantId tenantId, UserId reactivatedBy, Instant occurredAt) {}
