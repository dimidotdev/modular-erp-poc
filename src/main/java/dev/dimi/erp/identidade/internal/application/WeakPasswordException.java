package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Senha apresentada não atende à política mínima. HTTP 422 (sintaticamente válido mas viola regra
 * de negócio).
 *
 * <p>Lista as violações específicas em {@code violations[]} para que o cliente exiba feedback
 * acionável ao usuário (UX legítima — não é anti-enumeração).
 */
public class WeakPasswordException extends DomainException {

  private final List<String> violations;

  public WeakPasswordException(List<String> violations) {
    super(
        "identidade.weak-password",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Senha não atende à política",
        "A senha apresentada não atende aos requisitos mínimos. Veja violations para detalhes.");
    this.violations = List.copyOf(violations);
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("violations", violations);
  }
}
