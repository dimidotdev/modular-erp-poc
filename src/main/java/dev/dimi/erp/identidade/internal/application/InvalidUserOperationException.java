package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação administrativa rejeitada por regra de domínio (ex: desativar a si mesmo, reativar conta
 * já deactivated).
 */
public class InvalidUserOperationException extends DomainException {

  private final String reason;

  public InvalidUserOperationException(String reason, String detail) {
    super(
        "identidade.invalid-user-operation",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Operação inválida",
        detail);
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("reason", reason);
  }
}
