package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.List;

/**
 * Claims tipadas de um access token JWT já validado.
 *
 * <p>Espelha o que {@link JwtTokenIssuer#issueAccessToken} produz. {@link JwtTokenValidator} é quem
 * constrói este record após validar assinatura, expiração e issuer.
 *
 * <p>{@code stepUpAt} é opcional: presente apenas em tokens emitidos por {@code /auth/step-up} para
 * marcar re-autenticação recente (consumido por endpoints sensíveis como anonimização LGPD).
 */
public record JwtClaims(
    UserId userId,
    String username,
    TenantId tenantId,
    String tenantSlug,
    SessionId sessionId,
    List<String> roles,
    List<String> permissions,
    List<String> amr,
    Instant stepUpAt) {

  public JwtClaims {
    if (userId == null) throw new IllegalArgumentException("sub (userId) obrigatório");
    if (username == null || username.isBlank())
      throw new IllegalArgumentException("uname (username) obrigatório");
    if (tenantId == null) throw new IllegalArgumentException("tid (tenantId) obrigatório");
    if (tenantSlug == null || tenantSlug.isBlank())
      throw new IllegalArgumentException("tslug (tenantSlug) obrigatório");
    if (sessionId == null) throw new IllegalArgumentException("sid (sessionId) obrigatório");
    roles = roles == null ? List.of() : List.copyOf(roles);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
    amr = amr == null ? List.of() : List.copyOf(amr);
  }
}
