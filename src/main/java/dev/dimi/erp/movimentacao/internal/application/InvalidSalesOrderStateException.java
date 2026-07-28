package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Transição de estado inválida em {@code sales_order} — ex: aprovar pedido REJECTED. */
public class InvalidSalesOrderStateException extends DomainException {

  private final SalesOrderStatus current;
  private final String attemptedAction;

  public InvalidSalesOrderStateException(SalesOrderStatus current, String attemptedAction) {
    super(
        "movimentacao.invalid-sales-order-state",
        HttpStatus.CONFLICT,
        "Estado do pedido inválido para esta operação",
        "Pedido está em estado %s; ação '%s' não permitida.".formatted(current, attemptedAction));
    this.current = current;
    this.attemptedAction = attemptedAction;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("currentStatus", current.name(), "attemptedAction", attemptedAction);
  }
}
