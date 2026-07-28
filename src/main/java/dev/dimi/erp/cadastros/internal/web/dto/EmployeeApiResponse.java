package dev.dimi.erp.cadastros.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.EmployeeSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Resposta HTTP plana para um {@link EmployeeSummary}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmployeeApiResponse(
    EmployeeId id,
    PersonId personId,
    BranchId branchId,
    UUID jobTitleId,
    UUID departmentId,
    BigDecimal salary,
    LocalDate hiredAt,
    LocalDate terminatedAt,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {

  public static EmployeeApiResponse from(EmployeeSummary s) {
    return new EmployeeApiResponse(
        s.id(),
        s.personId(),
        s.branchId(),
        s.jobTitleId(),
        s.departmentId(),
        s.salary(),
        s.hiredAt(),
        s.terminatedAt(),
        s.active(),
        s.notes(),
        s.createdAt(),
        s.updatedAt());
  }
}
