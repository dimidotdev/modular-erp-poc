package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Slug de tenant inválido ou inexistente. Reutilizamos a mesma resposta de credenciais inválidas
 * ({@code 401}) para não vazar quais tenants existem na plataforma — defesa contra enumeração.
 */
public class TenantNotFoundException extends DomainException {

  public TenantNotFoundException(String slugAttempted) {
    super(
        "identidade.invalid-credentials",
        HttpStatus.UNAUTHORIZED,
        "Credenciais inválidas",
        "Usuário ou senha incorretos.");
  }
}
