package dev.dimi.erp.shared.documents;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erro 400 quando uma string não representa um CNPJ válido. */
public class InvalidCnpjException extends DomainException {

  private final String input;

  public InvalidCnpjException(String input, String detail) {
    super("documents.invalid-cnpj", HttpStatus.BAD_REQUEST, "CNPJ inválido", detail);
    this.input = input;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("input", input == null ? "" : input);
  }
}
