package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * OS referenciada por {@code POST /os/{id}/bill} não está em status faturável (ex.: {@code OPEN}
 * ainda em execução, {@code CANCELLED}, ou já {@code BILLED}). Faturamento exige OS pronta — em
 * outros status há risco de mutação ou de duplicidade.
 */
public class OsNotBillableException extends DomainException {

  public OsNotBillableException(ServiceOrderId osId, String currentStatusCode) {
    super(
        "os.not-billable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "OS não pode ser faturada",
        "OS "
            + osId.value()
            + " está em status '"
            + currentStatusCode
            + "' — faturamento recusado.");
  }
}
