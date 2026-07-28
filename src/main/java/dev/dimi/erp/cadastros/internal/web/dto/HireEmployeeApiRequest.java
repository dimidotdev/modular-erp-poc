package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.HireEmployeeCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request body do {@code POST /api/v1/cadastros/employees}. */
public record HireEmployeeApiRequest(
    @NotNull UUID personId,
    @NotNull UUID branchId,
    UUID jobTitleId,
    UUID departmentId,
    @Positive BigDecimal salary,
    @NotNull @PastOrPresent LocalDate hiredAt,
    @Size(max = 1000) String notes) {

  public HireEmployeeCommand toCommand(UserId hiredBy) {
    return new HireEmployeeCommand(
        new PersonId(personId),
        new BranchId(branchId),
        jobTitleId,
        departmentId,
        salary,
        hiredAt,
        notes,
        hiredBy);
  }
}
