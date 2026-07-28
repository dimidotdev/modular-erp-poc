package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Tenant existe mas está SUSPENDED ou TERMINATED — bloqueia login. Diferente de {@link
 * TenantNotFoundException}, aqui revelamos o estado (status do tenant é informação operacional;
 * cliente legitimo precisa saber que o acesso foi pausado).
 */
public class TenantSuspendedException extends DomainException {

  public TenantSuspendedException(String tenantSlug) {
    super(
        "identidade.tenant-suspended",
        HttpStatus.FORBIDDEN,
        "Tenant suspenso",
        "O tenant '" + tenantSlug + "' está suspenso. Contate o administrador da plataforma.");
  }
}
