package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Senha do PFX não confere com o arquivo enviado. RN-CAD-007: erro retornado pelo upload de
 * certificado quando o material entrega resposta válida do KeyStore mas a senha falha em decifrar a
 * private key.
 *
 * <p>HTTP 422 (não 401/403): o problema é semântico do payload, não auth da requisição. Code {@code
 * cadastros.invalid-pfx-password} é estável entre versões para o cliente disambiguar de outros
 * erros 422.
 */
public class InvalidPkcs12PasswordException extends DomainException {

  public InvalidPkcs12PasswordException() {
    super(
        "cadastros.invalid-pfx-password",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Senha do certificado inválida",
        "A senha informada não decifra o PKCS#12 enviado.");
  }
}
