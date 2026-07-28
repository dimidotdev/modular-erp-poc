package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Tentativa de atribuir uma role cujo {@code code} não existe no tenant. */
public class RoleNotFoundException extends DomainException {

  private final String roleCode;

  public RoleNotFoundException(String roleCode) {
    super(
        "identidade.role-not-found",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Role inexistente",
        "A role informada não existe neste tenant.");
    this.roleCode = roleCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("roleCode", roleCode);
  }
}
