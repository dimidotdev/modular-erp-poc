package dev.dimi.erp.shared.modularization;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Requisição atingiu um path coberto por um módulo desativado no tenant atual. Responde HTTP 404 (e
 * não 403) deliberadamente — não vazar para o cliente a existência de funcionalidade que ele não
 * contratou.
 */
public class ModuleAccessDeniedException extends DomainException {

  private final String moduleCode;

  public ModuleAccessDeniedException(String moduleCode) {
    super(
        "module.not-enabled",
        HttpStatus.NOT_FOUND,
        "Recurso não disponível",
        "O módulo necessário para este recurso não está ativo neste tenant.");
    this.moduleCode = moduleCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("moduleCode", moduleCode);
  }
}
