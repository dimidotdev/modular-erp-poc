package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.List;

/**
 * Login bem-sucedido. Consumido por {@code auditoria} (registra evento {@code LOGIN_SUCCEEDED}) e
 * por qualquer módulo interessado em rastrear sessões ativas.
 *
 * <p>{@code amr} (Authentication Methods References) lista os fatores usados — atualmente {@code
 * ["pwd"]}; quando MFA chegar, vira {@code ["pwd","totp"]} ou {@code ["pwd","webauthn"]}.
 */
public record LoginSucceededEvent(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    SessionId sessionId,
    List<String> amr,
    String ip,
    String userAgent,
    Instant occurredAt) {

  public LoginSucceededEvent {
    amr = amr == null ? List.of() : List.copyOf(amr);
  }
}
