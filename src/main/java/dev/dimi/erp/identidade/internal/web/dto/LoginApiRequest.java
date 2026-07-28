package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.internal.application.LoginCommand;
import dev.dimi.erp.identidade.internal.web.ClientRequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body do endpoint {@code POST /api/v1/auth/login}. */
public record LoginApiRequest(
    @NotBlank @Size(max = 64) String tenantSlug,
    @NotBlank @Size(max = 200) String identifier,
    @NotBlank @Size(min = 1, max = 200) String password) {

  /** Converte para o command da camada de aplicação, capturando IP e User-Agent. */
  public LoginCommand toCommand(HttpServletRequest request) {
    return new LoginCommand(
        tenantSlug,
        identifier,
        password,
        ClientRequestMetadata.clientIp(request),
        ClientRequestMetadata.userAgent(request));
  }
}
