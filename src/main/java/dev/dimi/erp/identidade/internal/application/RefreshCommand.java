package dev.dimi.erp.identidade.internal.application;

/**
 * Comando de refresh de tokens.
 *
 * <p>{@code rawRefreshToken} é o token completo entregue ao cliente no login (formato {@code
 * <tenantSlug>.<base64>}). O service extrai o slug, resolve o tenant, valida o hash e rotaciona.
 */
public record RefreshCommand(String rawRefreshToken, String ip, String userAgent) {

  public RefreshCommand {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new IllegalArgumentException("rawRefreshToken obrigatório");
    }
  }
}
