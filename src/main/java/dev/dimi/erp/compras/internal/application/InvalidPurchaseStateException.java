package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Transição inválida em SC, cotação ou PC. */
public class InvalidPurchaseStateException extends DomainException {

  private final String entity;
  private final String currentStatus;
  private final String attemptedAction;

  public InvalidPurchaseStateException(
      String entity, String currentStatus, String attemptedAction) {
    super(
        "compras.invalid-state",
        HttpStatus.CONFLICT,
        "Estado inválido para esta operação",
        "%s está em estado %s; ação '%s' não permitida."
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
