package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Modalidade ativada para uma pessoa via {@code POST
 * /api/v1/cadastros/pessoas/{id}/modalidades/{modalidade}}.
 *
 * <p>Cada modalidade ({@code CUSTOMER}, {@code SUPPLIER}, {@code CARRIER}, {@code SELLER}, {@code
 * EMPLOYEE}) abre um conjunto distinto de capacidades operacionais. Outros módulos podem reagir ao
 * evento (ex: financeiro pode pré-criar saldo de cliente).
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code MODALITY_ACTIVATED}).
 */
public record ModalityActivatedEvent(
    PersonId personId,
    TenantId tenantId,
    Modality modality,
    UserId activatedBy,
    Instant occurredAt) {}
