package dev.dimi.erp.identidade.internal.application;

/**
 * Comando de login. {@code identifier} pode ser username ou email — {@code LoginService} resolve
 * via {@code UserAccountRepository#findByUsernameOrEmail}.
 */
public record LoginCommand(
    String tenantSlug, String identifier, String password, String ip, String userAgent) {

  public LoginCommand {
    if (tenantSlug == null || tenantSlug.isBlank())
      throw new IllegalArgumentException("tenantSlug obrigatório");
    if (identifier == null || identifier.isBlank())
      throw new IllegalArgumentException("identifier obrigatório");
    if (password == null || password.isEmpty())
      throw new IllegalArgumentException("password obrigatório");
  }
}
