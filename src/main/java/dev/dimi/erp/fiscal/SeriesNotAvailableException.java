package dev.dimi.erp.fiscal;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Série fiscal inexistente ou desativada para a filial+modelo. O endpoint de cadastro de séries
 * entra no slice 8.6 — em 8.2 a série precisa ser semeada manualmente (ou pelos ITs).
 */
public class SeriesNotAvailableException extends DomainException {

  public SeriesNotAvailableException(String branchId, FiscalModel model, String series) {
    super(
        "fiscal.series-not-available",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Série fiscal indisponível",
        "Série "
            + series
            + " ("
            + model
            + ") indisponível para filial "
            + branchId
            + " — cadastre ou ative em /api/v1/fiscal/series (slice 8.6).");
  }
}
