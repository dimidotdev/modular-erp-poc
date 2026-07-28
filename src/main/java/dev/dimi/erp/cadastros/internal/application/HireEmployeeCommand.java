package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando para contratar um funcionário vinculando uma {@link PersonId} a uma {@link BranchId}.
 *
 * <p>{@code jobTitleId}, {@code departmentId}, {@code salary} e {@code notes} são opcionais.
 * Service garante RN-CAD-010 (único ativo por person+branch).
 */
public record HireEmployeeCommand(
    PersonId personId,
    BranchId branchId,
    UUID jobTitleId,
    UUID departmentId,
    BigDecimal salary,
    LocalDate hiredAt,
    String notes,
    UserId hiredBy) {

  public HireEmployeeCommand {
    if (personId == null) {
      throw new IllegalArgumentException("personId obrigatório");
    }
    if (branchId == null) {
      throw new IllegalArgumentException("branchId obrigatório");
    }
    if (hiredAt == null) {
      throw new IllegalArgumentException("hiredAt obrigatório");
    }
  }
}
