package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.application.LoginResult;

/**
 * Response do {@code POST /api/v1/auth/login}.
 *
 * <p>{@code accessToken} é JWT HS256 com TTL curto (15min). {@code refreshToken} é random one-shot
 * — aparece uma única vez nesta resposta. Cliente deve armazenar com cuidado (cookie HttpOnly +
 * Secure + SameSite=Strict é recomendado; localStorage só se SPA exigir).
 *
 * <p>{@code accessTokenExpiresInSeconds} e {@code refreshTokenExpiresInSeconds} são TTLs em
 * segundos para o cliente programar renovação proativa.
 *
 * <p>{@code mustChangePassword=true} → cliente deve direcionar usuário para change-password antes
 * de qualquer outra operação.
 */
public record LoginApiResponse(
    UserId userId,
    SessionId sessionId,
    String tokenType,
    String accessToken,
    long accessTokenExpiresInSeconds,
    String refreshToken,
    long refreshTokenExpiresInSeconds,
    boolean mustChangePassword) {

  public static LoginApiResponse from(LoginResult r) {
    return new LoginApiResponse(
        r.userId(),
        r.sessionId(),
        "Bearer",
        r.accessToken(),
        r.accessTokenTtl().toSeconds(),
        r.refreshToken(),
        r.refreshTokenTtl().toSeconds(),
        r.mustChangePassword());
  }
}
