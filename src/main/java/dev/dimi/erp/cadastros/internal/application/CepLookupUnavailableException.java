package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.address.Cep;
import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Provedor de CEP (ViaCEP) está indisponível: timeout, 5xx, conexão recusada ou resposta inválida.
 *
 * <p>Mapeia para {@code 502 Bad Gateway} — o erro é upstream, não da nossa app. O cliente pode
 * tentar novamente ou preencher o endereço manualmente (RN-CAD-015 trata lookup como opcional).
 */
public class CepLookupUnavailableException extends DomainException {

  public CepLookupUnavailableException(Cep cep, String reason, Throwable cause) {
    super(
        "cadastros.cep-lookup-unavailable",
        HttpStatus.BAD_GATEWAY,
        "Lookup de CEP indisponível",
        "Provedor de CEP não respondeu para " + cep.formatted() + ": " + reason,
        cause);
  }
}
