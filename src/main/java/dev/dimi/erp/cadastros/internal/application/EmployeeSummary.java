package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.Employee;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Projeção plana de {@link Employee} para resposta de service. */
public record EmployeeSummary(
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

  public static EmployeeSummary of(Employee e) {
    return new EmployeeSummary(
        e.id(),
        new PersonId(e.getPersonId()),
        new BranchId(e.getBranchId()),
        e.getJobTitleId(),
        e.getDepartmentId(),
        e.getSalary(),
        e.getHiredAt(),
        e.getTerminatedAt(),
        e.isActive(),
        e.getNotes(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
