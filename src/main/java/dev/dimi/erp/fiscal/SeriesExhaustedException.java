package dev.dimi.erp.fiscal;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Número da série atingiu o limite legal SEFAZ (999.999.999). Emissão recusada com 422 — o operador
 * deve abrir uma nova série.
 */
public class SeriesExhaustedException extends DomainException {

  public SeriesExhaustedException(String series, FiscalModel model) {
    super(
        "fiscal.series-exhausted",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Série fiscal esgotada",
        "Série " + series + " (" + model + ") esgotada — abra uma nova série.");
  }
}
