package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Todas as sessões ativas de um usuário foram revogadas.
 *
 * <p>Disparado em três cenários (todos com {@code reason} machine-readable):
 *
 * <ul>
 *   <li>{@code REUSE_DETECTED}: tentativa de uso de refresh token já consumido — defesa contra
 *       roubo de token.
 *   <li>{@code LOGOUT_ALL}: usuário (ou admin) acionou logout em todos os dispositivos.
 *   <li>{@code PASSWORD_CHANGED}: troca de senha invalida todas as sessões anteriores.
 * </ul>
 *
 * <p>Consumido por {@code auditoria} (categoria {@code AUTH}, severity elevada para {@code
 * REUSE_DETECTED}) e por sistemas de alerta (Slack, email ao usuário) — quando MFA/notificações
 * estiverem implementadas em slices futuros.
 */
public record SessionRevokedAllEvent(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    String reason,
    int tokensRevoked,
    String triggeringIp,
    Instant occurredAt) {}
