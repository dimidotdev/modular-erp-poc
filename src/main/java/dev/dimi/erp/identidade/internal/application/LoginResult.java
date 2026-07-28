package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import java.time.Duration;

/**
 * Resultado bem-sucedido de login.
 *
 * <p>{@code accessToken} e {@code refreshToken} são valores em CLARO — aparecem uma única vez nesta
 * resposta. O refresh nunca é persistido em claro (só seu hash SHA-256), portanto se cliente perde,
 * precisa reautenticar.
 */
public record LoginResult(
    UserId userId,
    SessionId sessionId,
    String accessToken,
    Duration accessTokenTtl,
    String refreshToken,
    Duration refreshTokenTtl,
    boolean mustChangePassword) {}
