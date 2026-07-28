package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Pessoa desativada via {@code PATCH /api/v1/cadastros/pessoas/{id}/desativar}.
 *
 * <p>Soft delete: a entidade permanece (referência fiscal preservada), apenas {@code is_active}
 * passa a {@code false}. Anonimização LGPD é evento separado e fica para o slice 3.4.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code PERSON_DEACTIVATED}).
 */
public record PersonDeactivatedEvent(
    PersonId personId,
    TenantId tenantId,
    String reason,
    UserId deactivatedBy,
    Instant occurredAt) {}
