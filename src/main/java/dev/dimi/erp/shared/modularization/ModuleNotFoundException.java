package dev.dimi.erp.shared.modularization;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Módulo solicitado não existe no {@link ModuleCatalog}. */
public class ModuleNotFoundException extends DomainException {

  private final String moduleCode;

  public ModuleNotFoundException(String moduleCode) {
    super(
        "module.not-found",
        HttpStatus.NOT_FOUND,
        "Módulo não encontrado",
        "Não existe módulo com este código no catálogo.");
    this.moduleCode = moduleCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("moduleCode", moduleCode);
  }
}
