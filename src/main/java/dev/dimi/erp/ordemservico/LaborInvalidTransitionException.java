package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Guarda defensiva: {@code finishedAt} anterior a {@code startedAt}. A constraint {@code
 * ck_os_labor_finished_after_started} no banco também protege, mas validação no domínio gera
 * mensagem amigável antes de chegar no INSERT/UPDATE.
 */
public class LaborInvalidTransitionException extends DomainException {

  private final Instant startedAt;
  private final Instant finishedAt;

  public LaborInvalidTransitionException(Instant startedAt, Instant finishedAt) {
    super(
        "os.labor-invalid-transition",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Apontamento com intervalo inválido",
        "Apontamento não pode ter finishedAt ("
            + finishedAt
            + ") anterior a startedAt ("
            + startedAt
            + ").");
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("startedAt", startedAt, "finishedAt", finishedAt);
  }
}
