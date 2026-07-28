package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.address.Cep;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * CEP tem 8 dígitos válidos no formato, mas o provedor (ViaCEP) não conhece esse logradouro.
 *
 * <p>Mapeia para {@code 404 Not Found}. Não é erro do provedor — é input inválido do usuário.
 */
public class CepNotFoundException extends DomainException {

  private final Cep cep;

  public CepNotFoundException(Cep cep) {
    super(
        "cadastros.cep-not-found",
        HttpStatus.NOT_FOUND,
        "CEP não encontrado",
        "CEP " + cep.formatted() + " não foi encontrado no provedor.");
    this.cep = cep;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("cep", cep.value());
  }
}
