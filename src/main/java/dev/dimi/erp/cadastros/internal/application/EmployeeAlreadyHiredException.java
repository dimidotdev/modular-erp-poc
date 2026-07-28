package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de contratar uma pessoa que já tem vínculo ativo na mesma filial (RN-CAD-010, índice
 * parcial {@code uq_employee_active}). Use {@code terminate} antes de re-contratar.
 */
public class EmployeeAlreadyHiredException extends DomainException {

  private final PersonId personId;
  private final BranchId branchId;

  public EmployeeAlreadyHiredException(PersonId personId, BranchId branchId) {
    super(
        "cadastros.employee-already-hired",
        HttpStatus.CONFLICT,
        "Funcionário já contratado nesta filial",
        "Já existe vínculo ativo desta pessoa na filial informada (RN-CAD-010).");
    this.personId = personId;
    this.branchId = branchId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("personId", personId.value().toString(), "branchId", branchId.value().toString());
  }
}
