package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** O ator tentou conceder uma role com privilégios que ele próprio não possui. */
public class RoleAssignmentForbiddenException extends DomainException {

  public RoleAssignmentForbiddenException(String roleCode) {
    super(
        "identidade.role-assignment-forbidden",
        HttpStatus.FORBIDDEN,
        "Atribuição de role não permitida",
        "O usuário autenticado não pode atribuir a role " + roleCode + ".");
  }
}
