package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação de estoque com argumentos semanticamente inválidos — ex: transferência mesma origem e
 * destino, reverse de movimento já estornado, código duplicado em location/batch dentro da mesma
 * filial/packaging.
 */
public class InvalidStockOperationException extends DomainException {

  private final String reason;

  public InvalidStockOperationException(String reason, String detail) {
    super("estoque.invalid-operation", HttpStatus.BAD_REQUEST, "Operação inválida", detail);
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("reason", reason);
  }
}
