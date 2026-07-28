package dev.dimi.erp.shared.address;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erro 400 quando uma string não representa um CEP válido. */
public class InvalidCepException extends DomainException {

  private final String input;

  public InvalidCepException(String input, String detail) {
    super("address.invalid-cep", HttpStatus.BAD_REQUEST, "CEP inválido", detail);
    this.input = input;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("input", input == null ? "" : input);
  }
}
