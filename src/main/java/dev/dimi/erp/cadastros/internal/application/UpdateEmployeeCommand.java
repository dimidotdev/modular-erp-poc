package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comando para atualização parcial de funcionário. Campos {@code null} são ignorados; {@code
 * personId}, {@code branchId} e {@code hiredAt} são imutáveis após contratação.
 */
public record UpdateEmployeeCommand(
    EmployeeId employeeId,
    UUID jobTitleId,
    UUID departmentId,
    BigDecimal salary,
    String notes,
    UserId updatedBy) {

  public UpdateEmployeeCommand {
    if (employeeId == null) {
      throw new IllegalArgumentException("employeeId obrigatório");
    }
  }
}
