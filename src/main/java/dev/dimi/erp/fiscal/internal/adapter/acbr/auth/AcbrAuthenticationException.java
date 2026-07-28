package dev.dimi.erp.fiscal.internal.adapter.acbr.auth;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Falha de autenticação OAuth2 contra o SaaS ACBr API (Keycloak). Mapeada para HTTP 502 (Bad
 * Gateway) porque o erro é de upstream — credenciais inválidas, Keycloak indisponível ou payload
 * inesperado.
 */
public class AcbrAuthenticationException extends DomainException {

  public AcbrAuthenticationException(String detail) {
    super(
        "upstream-auth-failed",
        HttpStatus.BAD_GATEWAY,
        "Falha de autenticação no provedor fiscal",
        detail);
  }

  public AcbrAuthenticationException(String detail, Throwable cause) {
    super(
        "upstream-auth-failed",
        HttpStatus.BAD_GATEWAY,
        "Falha de autenticação no provedor fiscal",
        detail,
        cause);
  }
}
