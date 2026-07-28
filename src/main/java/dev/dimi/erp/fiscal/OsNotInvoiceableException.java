package dev.dimi.erp.fiscal;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Ordem de Serviço referenciada por {@code POST /fiscal/invoices/from-os/{id}} não existe ou não
 * está em status faturável. Análoga a {@link SaleNotInvoiceableException}, mas para o agregado OS:
 * o módulo {@code ordemservico} só projeta via {@code OsQueryPort.findForInvoicing} as OSs prontas
 * para faturamento — em {@code OPEN}, {@code CANCELLED} ou já {@code BILLED} a projeção volta
 * vazia, e este 422 é o que o controller fiscal devolve ao caller.
 */
public class OsNotInvoiceableException extends DomainException {

  public OsNotInvoiceableException(String osId) {
    super(
        "fiscal.os-not-invoiceable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "OS não elegível para emissão fiscal",
        "OS " + osId + " não existe ou não está em status faturável — emissão fiscal recusada.");
  }
}
