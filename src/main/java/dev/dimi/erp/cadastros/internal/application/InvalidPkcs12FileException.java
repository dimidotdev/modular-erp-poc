package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Arquivo enviado não é um PKCS#12 válido (corrupto, formato errado, ou sem certificado dentro).
 * Diferente de {@link InvalidPkcs12PasswordException}, que é "arquivo válido + senha errada".
 */
public class InvalidPkcs12FileException extends DomainException {

  public InvalidPkcs12FileException(String detail) {
    super(
        "cadastros.invalid-pfx-file",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Arquivo PKCS#12 inválido",
        detail);
  }

  public InvalidPkcs12FileException(String detail, Throwable cause) {
    super(
        "cadastros.invalid-pfx-file",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Arquivo PKCS#12 inválido",
        detail,
        cause);
  }
}
