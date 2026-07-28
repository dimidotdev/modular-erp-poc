package dev.dimi.erp.shared.documents;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Erro 400 quando uma string não representa um CPF válido. */
public class InvalidCpfException extends DomainException {

  private final String input;

  public InvalidCpfException(String input, String detail) {
    super("documents.invalid-cpf", HttpStatus.BAD_REQUEST, "CPF inválido", detail);
    this.input = input;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("input", input == null ? "" : input);
  }
}
