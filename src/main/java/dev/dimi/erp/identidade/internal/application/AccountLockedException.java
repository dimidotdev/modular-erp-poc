package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativas de login excessivas — usuário ou IP em lockout.
 *
 * <p>HTTP 429 com header {@code Retry-After} (segundos) seguindo RFC 9110. Mensagem genérica não
 * revela se o bloqueio é por usuário ou por IP (anti-enumeração).
 */
public class AccountLockedException extends DomainException {

  private final Duration retryAfter;

  public AccountLockedException(Duration retryAfter) {
    super(
        "identidade.too-many-attempts",
        HttpStatus.TOO_MANY_REQUESTS,
        "Muitas tentativas",
        "Excesso de tentativas de login. Tente novamente em alguns minutos.");
    this.retryAfter = retryAfter;
  }

  @Override
  public Map<String, String> responseHeaders() {
    long seconds = Math.max(1, retryAfter.toSeconds());
    return Map.of("Retry-After", String.valueOf(seconds));
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("retryAfterSeconds", Math.max(1, retryAfter.toSeconds()));
  }
}
