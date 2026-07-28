package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Credenciais inválidas no login.
 *
 * <p>Mensagem deliberadamente genérica — não revela se foi usuário inexistente, senha errada ou
 * tenant inválido (anti-enumeração). Para diagnóstico, o motivo real é registrado em {@code
 * login_attempt.failure_reason} e no audit log via {@code LoginFailedEvent}.
 */
public class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException() {
    super(
        "identidade.invalid-credentials",
        HttpStatus.UNAUTHORIZED,
        "Credenciais inválidas",
        "Usuário ou senha incorretos.");
  }
}
