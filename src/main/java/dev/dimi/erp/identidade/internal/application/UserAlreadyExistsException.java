package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de criar usuário com {@code username} ou {@code email} já existentes no tenant. Campo
 * em conflito ({@code username}/{@code email}) e valor são expostos para ajudar o front a sinalizar
 * o input certo.
 */
public class UserAlreadyExistsException extends DomainException {

  private final String field;
  private final String value;

  public UserAlreadyExistsException(String field, String value) {
    super(
        "identidade.user-already-exists",
        HttpStatus.CONFLICT,
        "Usuário já existe",
        "Já existe um usuário com este " + field + " neste tenant.");
    this.field = field;
    this.value = value;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("field", field, "value", value);
  }
}
