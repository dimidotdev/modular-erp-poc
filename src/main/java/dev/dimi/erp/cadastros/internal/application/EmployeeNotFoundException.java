package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Vínculo {@code employee} não encontrado no tenant atual. */
public class EmployeeNotFoundException extends DomainException {

  private final EmployeeId id;

  public EmployeeNotFoundException(EmployeeId id) {
    super(
        "cadastros.employee-not-found",
        HttpStatus.NOT_FOUND,
        "Funcionário não encontrado",
        "Nenhum funcionário com este id no tenant atual.");
    this.id = id;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("employeeId", id.value().toString());
  }
}
