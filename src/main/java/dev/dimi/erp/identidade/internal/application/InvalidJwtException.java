package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * JWT apresentado é inválido (assinatura, formato, expirado, issuer, ou claim obrigatório ausente).
 *
 * <p>Mensagem genérica deliberada — não revela o motivo específico para o cliente. Cliente deve
 * tentar refresh; se refresh falhar também, reautenticar.
 */
public class InvalidJwtException extends DomainException {

  public InvalidJwtException() {
    super(
        "identidade.invalid-jwt",
        HttpStatus.UNAUTHORIZED,
        "Token de acesso inválido",
        "O token de acesso apresentado é inválido ou expirou.");
  }

  public InvalidJwtException(Throwable cause) {
    super(
        "identidade.invalid-jwt",
        HttpStatus.UNAUTHORIZED,
        "Token de acesso inválido",
        "O token de acesso apresentado é inválido ou expirou.",
        cause);
  }
}
