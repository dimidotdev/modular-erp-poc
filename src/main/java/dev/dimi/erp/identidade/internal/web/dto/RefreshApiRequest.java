package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.internal.application.RefreshCommand;
import dev.dimi.erp.identidade.internal.web.ClientRequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body do endpoint {@code POST /api/v1/auth/refresh}. */
public record RefreshApiRequest(@NotBlank @Size(max = 256) String refreshToken) {

  public RefreshCommand toCommand(HttpServletRequest request) {
    return new RefreshCommand(
        refreshToken,
        ClientRequestMetadata.clientIp(request),
        ClientRequestMetadata.userAgent(request));
  }
}
