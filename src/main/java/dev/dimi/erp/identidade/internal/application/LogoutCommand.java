package dev.dimi.erp.identidade.internal.application;

/**
 * Comando de logout. {@code rawRefreshToken} é o token completo (com slug embutido) — usamos para
 * resolver o tenant e identificar a sessão/usuário a revogar.
 *
 * <p>Após Etapa 7 (filter JWT), também aceitaremos access token no header como alternativa. v1 só
 * refresh body.
 */
public record LogoutCommand(String rawRefreshToken, boolean allSessions) {

  public LogoutCommand {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new IllegalArgumentException("rawRefreshToken obrigatório");
    }
  }
}
