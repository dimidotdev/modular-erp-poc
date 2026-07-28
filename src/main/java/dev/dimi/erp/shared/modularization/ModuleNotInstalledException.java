package dev.dimi.erp.shared.modularization;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de alternar (PATCH) um módulo presente no {@link ModuleCatalog} mas com {@code
 * installed=false} — backend ainda não implementado, módulo está no catálogo apenas para mockup de
 * roadmap no frontend.
 */
public class ModuleNotInstalledException extends DomainException {

  private final String moduleCode;

  public ModuleNotInstalledException(String moduleCode) {
    super(
        "module.not-installed",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Módulo não instalado",
        "O módulo '" + moduleCode + "' ainda não está disponível neste produto.");
    this.moduleCode = moduleCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("moduleCode", moduleCode);
  }
}
