package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Percentual de reajuste fora do range razoável aceito em v1: {@code [-50, +500]}. Limites existem
 * para evitar erro de digitação (ex.: digitar {@code 1500} em vez de {@code 15.00}) — não são
 * limite legal/regulatório.
 */
public class InvalidAdjustmentPercentageException extends DomainException {

  private final BigDecimal percentage;

  public InvalidAdjustmentPercentageException(BigDecimal percentage) {
    super(
        "adjustment.invalid-percentage",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Percentual de reajuste inválido",
        "Percentual "
            + percentage
            + " fora do range aceito em v1 [-50, +500]. Verifique a digitação.");
    this.percentage = percentage;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("percentage", percentage.toPlainString());
  }
}
