package dev.dimi.erp.fiscal;

/**
 * Falha técnica na comunicação com o provedor fiscal (timeout, conexão, payload inválido). Não
 * confundir com {@link AuthorizationResult.Rejected}, que representa rejeição semântica da SEFAZ.
 *
 * <p>Sem uma política de contingência ativa, a exceção chega ao controller como {@code 502 Bad
 * Gateway}. Uma política fiscal pode interceptá-la para acionar contingência (RN-FIS-013).
 */
public class FiscalProviderException extends RuntimeException {

  public FiscalProviderException(String message) {
    super(message);
  }

  public FiscalProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}
