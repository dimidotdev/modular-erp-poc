package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação semanticamente inválida em compras — ex: SC sem itens, cotação sem fornecedores,
 * resposta para item inexistente na SC, geração de PC sem decisão por item.
 */
public class InvalidPurchaseOperationException extends DomainException {

  private final String reason;

  public InvalidPurchaseOperationException(String reason, String detail) {
    super("compras.invalid-operation", HttpStatus.BAD_REQUEST, "Operação inválida", detail);
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("reason", reason);
  }
}
