package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;

/**
 * Comando de step-up. Identifica a sessão (já autenticada via JWT) e fornece a senha para
 * re-validação.
 */
public record StepUpCommand(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    SessionId sessionId,
    String username,
    String password,
    String ip,
    String userAgent) {

  public StepUpCommand {
    if (userId == null) throw new IllegalArgumentException("userId obrigatório");
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatório");
    if (tenantSlug == null || tenantSlug.isBlank())
      throw new IllegalArgumentException("tenantSlug obrigatório");
    if (sessionId == null) throw new IllegalArgumentException("sessionId obrigatório");
    if (username == null || username.isBlank())
      throw new IllegalArgumentException("username obrigatório");
    if (password == null || password.isEmpty())
      throw new IllegalArgumentException("password obrigatório");
  }
}
