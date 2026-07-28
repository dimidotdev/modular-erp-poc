package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Tentativa de login que falhou. Consumido por {@code auditoria} (categoria {@code AUTH}) e por
 * componentes de detecção de força bruta.
 *
 * <p>{@code usernameAttempted} é o que o cliente enviou — preserva o caso original para análise
 * forense. {@code tenantId}/{@code tenantSlug} podem ser {@code null} quando a falha aconteceu
 * antes de resolver o tenant (slug inválido/inexistente) — listener de audit descarta esses casos
 * (sem tenantId não há chave em {@code audit.audit_event}).
 *
 * <p>{@code reason} segue um código curto, kebab-case, para machine-readable: {@code
 * tenant-not-found}, {@code tenant-suspended}, {@code user-not-found}, {@code invalid-password},
 * {@code user-suspended}, {@code user-deactivated}.
 */
public record LoginFailedEvent(
    String usernameAttempted,
    TenantId tenantId,
    String tenantSlug,
    String reason,
    String ip,
    String userAgent,
    Instant occurredAt) {}
