package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de cadastrar filial com {@code code} já existente no tenant ou registrar {@code
 * branch_fiscal} com CNPJ já presente em outra filial.
 */
public class BranchAlreadyExistsException extends DomainException {

  private final String field;
  private final String value;

  public BranchAlreadyExistsException(String field, String value) {
    super(
        "cadastros.branch-already-exists",
        HttpStatus.CONFLICT,
        "Filial duplicada",
        "Já existe filial com este " + field + " neste tenant.");
    this.field = field;
    this.value = value;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("field", field, "value", value);
  }
}
