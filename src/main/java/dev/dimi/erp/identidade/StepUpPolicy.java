package dev.dimi.erp.identidade;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Política de step-up reutilizável por endpoints de operações sensíveis.
 *
 * <p>Exemplo:
 *
 * <pre>{@code
 * @PostMapping("/persons/{id}/anonimizar")
 * @PreAuthorize("hasAuthority('pessoa.anonimizar')")
 * ResponseEntity<Void> anonimizar(@AuthenticationPrincipal AuthenticatedUser principal, ...) {
 *   stepUpPolicy.requireFresh(principal);
 *   ...
 * }
 * }</pre>
 *
 * <p>Janela de frescor lida de {@code erp-poc.security.jwt.step-up-ttl} (PT5M por default). Quando
 * o principal não carrega claim {@code sua} ou ela está fora da janela, lança {@link
 * StepUpRequiredException} (HTTP 401 com {@code WWW-Authenticate: StepUp}).
 */
@Component
public class StepUpPolicy {

  private final Duration stepUpTtl;

  StepUpPolicy(@Value("${erp-poc.security.jwt.step-up-ttl}") Duration stepUpTtl) {
    this.stepUpTtl = stepUpTtl;
  }

  /** Exige que o principal carregue step-up dentro da janela; caso contrário lança 401. */
  public void requireFresh(AuthenticatedUser principal) {
    if (principal == null || !principal.hasFreshStepUp(stepUpTtl)) {
      throw new StepUpRequiredException();
    }
  }

  /** Janela de frescor configurada — útil para metadata em respostas, docs, testes. */
  public Duration stepUpTtl() {
    return stepUpTtl;
  }
}
