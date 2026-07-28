package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.internal.application.LogoutCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body de {@code POST /api/v1/auth/logout} e {@code /auth/logout-all}.
 *
 * <p>{@code refreshToken} é o token completo (com slug embutido); o service identifica
 * sessão/usuário a partir dele. v1 só aceita refresh no body; futuramente (após JWT filter da Etapa
 * 7) também access token no header.
 */
public record LogoutApiRequest(@NotBlank @Size(max = 256) String refreshToken) {

  public LogoutCommand toCommand(boolean allSessions) {
    return new LogoutCommand(refreshToken, allSessions);
  }
}
