package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Aditivo não encontrado no tenant atual. HTTP 404. */
public class AmendmentNotFoundException extends DomainException {

  public AmendmentNotFoundException(AmendmentId id) {
    super(
        "amendment.not-found",
        HttpStatus.NOT_FOUND,
        "Aditivo não encontrado",
        "Aditivo " + id.value() + " não existe no tenant atual.");
  }
}
