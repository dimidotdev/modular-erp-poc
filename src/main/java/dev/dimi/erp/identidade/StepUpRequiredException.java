package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação sensível exige re-autenticação recente (step-up).
 *
 * <p>Lançada por endpoints que pedem step-up quando a claim {@code sua} do JWT atual está ausente
 * ou fora da janela de frescor configurada ({@code erp-poc.security.jwt.step-up-ttl}).
 *
 * <p>HTTP 401 com header {@code WWW-Authenticate: StepUp realm="erp-poc", error="step_up_required"}
 * — o front lê o desafio e redireciona para o fluxo de step-up (re-prompt de senha) antes de
 * reapresentar a operação.
 */
public class StepUpRequiredException extends DomainException {

  public StepUpRequiredException() {
    super(
        "identidade.step-up-required",
        HttpStatus.UNAUTHORIZED,
        "Re-autenticação necessária",
        "Operação sensível exige confirmação recente de senha.");
  }

  @Override
  public Map<String, String> responseHeaders() {
    return Map.of("WWW-Authenticate", "StepUp realm=\"erp-poc\", error=\"step_up_required\"");
  }
}
