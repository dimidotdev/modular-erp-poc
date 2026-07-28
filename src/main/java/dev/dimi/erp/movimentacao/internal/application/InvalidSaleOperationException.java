package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação de venda/pedido com argumentos semanticamente inválidos — ex: pedido sem itens, item com
 * quantidade zero, sem preço autoritativo para a packaging+filial+data.
 */
public class InvalidSaleOperationException extends DomainException {

  private final String reason;

  public InvalidSaleOperationException(String reason, String detail) {
    super(
        "movimentacao.invalid-sale-operation", HttpStatus.BAD_REQUEST, "Operação inválida", detail);
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("reason", reason);
  }
}
