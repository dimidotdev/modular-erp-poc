package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.internal.application.StepUpCommand;
import dev.dimi.erp.identidade.internal.web.ClientRequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body do endpoint {@code POST /api/v1/auth/step-up}.
 *
 * <p>Identidade, tenant e sessão vêm do JWT atual (principal autenticado) — só a senha precisa ser
 * provida pelo cliente.
 */
public record StepUpApiRequest(@NotBlank @Size(min = 1, max = 200) String password) {

  public StepUpCommand toCommand(AuthenticatedUser principal, HttpServletRequest request) {
    return new StepUpCommand(
        principal.userId(),
        principal.tenantId(),
        principal.tenantSlug(),
        principal.sessionId(),
        principal.username(),
        password,
        ClientRequestMetadata.clientIp(request),
        ClientRequestMetadata.userAgent(request));
  }
}
