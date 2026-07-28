package dev.dimi.erp.fiscal.internal.adapter.acbr.auth;

import java.time.Instant;

/**
 * Token OAuth2 cacheado em memória pelo {@link AcbrTokenIssuer}.
 *
 * <p>{@code expiresAt} é calculado como {@code Instant.now() + expires_in} no momento da emissão,
 * para evitar drift entre relógio do client e do Keycloak.
 *
 * <p>{@link #isExpired(Instant, long)} aplica um buffer (default 60s no caller) para forçar refresh
 * proativo antes da expiração real e evitar 401 em chamadas concorrentes.
 */
public record AcbrAccessToken(String value, Instant expiresAt) {

  public boolean isExpired(Instant now, long bufferSeconds) {
    return !now.plusSeconds(bufferSeconds).isBefore(expiresAt);
  }
}
