package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Pessoa anonimizada (RN-CAD-011/012). Consumido por {@code auditoria} (categoria {@code LGPD},
 * tipo {@code PERSON_ANONYMIZED}).
 *
 * <p>{@code reason} registra a justificativa (ex: pedido do titular, retenção legal expirada). Não
 * carrega o estado anterior — operação é irreversível e o motivo do registro é compliance, não
 * recuperação.
 */
public record PersonAnonymizedEvent(
    PersonId personId, TenantId tenantId, String reason, UserId anonymizedBy, Instant occurredAt) {}
