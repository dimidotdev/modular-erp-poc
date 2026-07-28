package dev.dimi.erp.shared.modularization;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de desativar um módulo marcado como {@code isCore=true} no {@link ModuleCatalog}.
 * Módulos core são pré-requisito do produto e não podem ser desligados em runtime.
 */
public class CoreModuleImmutableException extends DomainException {

  private final String moduleCode;

  public CoreModuleImmutableException(String moduleCode) {
    super(
        "module.core-immutable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Módulo core não pode ser desativado",
        "O módulo '" + moduleCode + "' é core e não pode ser desativado neste tenant.");
    this.moduleCode = moduleCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("moduleCode", moduleCode);
  }
}
