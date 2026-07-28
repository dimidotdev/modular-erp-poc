package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Dado de produto que viola invariante semântica não capturada por Bean Validation — ex: NCM
 * ausente para classificação ≠ SERVICO (RN-PRD-002).
 */
public class InvalidProductDataException extends DomainException {

  private final String reason;

  public InvalidProductDataException(String reason, String detail) {
    super(
        "produto.invalid-product-data",
        HttpStatus.BAD_REQUEST,
        "Dados de produto inválidos",
        detail);
    this.reason = reason;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("reason", reason);
  }
}
