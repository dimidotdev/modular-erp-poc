package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Modalidade desativada para uma pessoa via {@code DELETE
 * /api/v1/cadastros/pessoas/{id}/modalidades/{modalidade}}.
 *
 * <p>Soft delete: o {@code person_modality_assignment} permanece com {@code is_active=false} para
 * preservar histórico (ex: para que vendas antigas de um cliente desativado continuem rastreáveis).
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code
 * MODALITY_DEACTIVATED}).
 */
public record ModalityDeactivatedEvent(
    PersonId personId,
    TenantId tenantId,
    Modality modality,
    String reason,
    UserId deactivatedBy,
    Instant occurredAt) {}
