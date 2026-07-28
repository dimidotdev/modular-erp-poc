package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Estado do vínculo {@code employee} é incompatível com a operação requisitada (ex: editar
 * funcionário já desligado, terminar duas vezes, terminate com data anterior à admissão).
 */
public class InvalidEmployeeStateException extends DomainException {

  private final EmployeeId id;
  private final String reason;

  public InvalidEmployeeStateException(EmployeeId id, String reason, String detail) {
    super(
        "cadastros.invalid-employee-state",
        HttpStatus.CONFLICT,
        "Estado de funcionário inválido para a operação",
        detail);
    this.id = id;
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("employeeId", id.value().toString(), "reason", reason);
  }
}
