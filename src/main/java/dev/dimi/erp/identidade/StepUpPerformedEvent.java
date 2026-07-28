package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Step-up (re-autenticação) executado com sucesso para uma sessão existente.
 *
 * <p>Consumido por {@code auditoria} para registrar o evento e ligar à operação sensível que
 * seguir. {@code stepUpAt} corresponde ao valor da claim {@code sua} emitida no novo access token.
 */
public record StepUpPerformedEvent(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    SessionId sessionId,
    String ip,
    String userAgent,
    Instant stepUpAt,
    Instant occurredAt) {}
