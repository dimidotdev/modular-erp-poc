package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.cadastros.internal.application.UpdateEmployeeCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body do {@code PATCH /api/v1/cadastros/employees/{id}}. Todos os campos são opcionais;
 * {@code null} mantém valor anterior.
 */
public record UpdateEmployeeApiRequest(
    UUID jobTitleId,
    UUID departmentId,
    @Positive BigDecimal salary,
    @Size(max = 1000) String notes) {

  public UpdateEmployeeCommand toCommand(EmployeeId employeeId, UserId updatedBy) {
    return new UpdateEmployeeCommand(
        employeeId, jobTitleId, departmentId, salary, notes, updatedBy);
  }
}
