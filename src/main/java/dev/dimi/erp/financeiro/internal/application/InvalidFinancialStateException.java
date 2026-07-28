package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Transição inválida em MVF ou pagamento (ex: cancelar MVF já PAID, renegociar MVF CANCELLED). */
public class InvalidFinancialStateException extends DomainException {

  private final String entity;
  private final String currentStatus;
  private final String attemptedAction;

  public InvalidFinancialStateException(
      String entity, String currentStatus, String attemptedAction) {
    super(
        "financeiro.invalid-state",
        HttpStatus.CONFLICT,
        "Estado inválido para esta operação",
        "%s em estado %s; ação '%s' não permitida."
            .formatted(entity, currentStatus, attemptedAction));
    this.entity = entity;
    this.currentStatus = currentStatus;
    this.attemptedAction = attemptedAction;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "entity", entity, "currentStatus", currentStatus, "attemptedAction", attemptedAction);
  }
}
