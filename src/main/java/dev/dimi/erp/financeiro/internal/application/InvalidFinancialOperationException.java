package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Operação financeira semanticamente inválida (rateio não bate, parcelas vazias, total inválido,
 * idempotency colidindo com payload diferente etc).
 */
public class InvalidFinancialOperationException extends DomainException {

  private final String detailCode;

  public InvalidFinancialOperationException(String detailCode, String message) {
    super(
        "financeiro.invalid-operation",
        HttpStatus.BAD_REQUEST,
        "Operação financeira inválida",
        message);
    this.detailCode = detailCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("detailCode", detailCode);
  }
}
