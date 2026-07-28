package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação solicitada conflita com o estado atual da pessoa.
 *
 * <p>Exemplos: tentar reativar pessoa anonimizada; tentar desativar pessoa já inativa; tentar
 * cadastrar PF sem CPF e sem flag de anonimização.
 */
public class InvalidPersonStateException extends DomainException {

  private final PersonId personId;
  private final String reason;

  public InvalidPersonStateException(PersonId personId, String reason, String detail) {
    super("cadastros.invalid-person-state", HttpStatus.CONFLICT, "Estado inválido", detail);
    this.personId = personId;
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "personId", personId == null ? "" : personId.value().toString(), "reason", reason);
  }
}
