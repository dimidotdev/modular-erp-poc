package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.internal.application.StepUpResult;
import java.time.Instant;

/**
 * Response do {@code POST /api/v1/auth/step-up}: novo access token carregando claim {@code sua}.
 *
 * <p>O refresh token NÃO é rotacionado — cliente continua usando o atual. {@code stepUpAt} é o
 * instante UTC válido da janela de frescor (cliente pode usar para decidir quando re-prompt).
 */
public record StepUpApiResponse(
    String tokenType, String accessToken, long accessTokenExpiresInSeconds, Instant stepUpAt) {

  public static StepUpApiResponse from(StepUpResult r) {
    return new StepUpApiResponse(
        "Bearer", r.accessToken(), r.accessTokenTtl().toSeconds(), r.stepUpAt());
  }
}
