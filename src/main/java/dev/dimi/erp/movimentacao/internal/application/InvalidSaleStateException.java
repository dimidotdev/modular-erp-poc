package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Transição de estado inválida em {@code sale} — ex: tentar mexer em CLOSED (RN-MOV-008). */
public class InvalidSaleStateException extends DomainException {

  private final SaleStatus current;
  private final String attemptedAction;

  public InvalidSaleStateException(SaleStatus current, String attemptedAction) {
    super(
        "movimentacao.invalid-sale-state",
        HttpStatus.CONFLICT,
        "Estado da venda inválido para esta operação",
        "Venda está em estado %s; ação '%s' não permitida.".formatted(current, attemptedAction));
    this.current = current;
    this.attemptedAction = attemptedAction;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("currentStatus", current.name(), "attemptedAction", attemptedAction);
  }
}
