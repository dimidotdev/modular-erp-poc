package dev.dimi.erp.fiscal;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Venda referenciada por {@code POST /fiscal/invoices/from-sale/{id}} não existe ou não está em
 * {@code CLOSED}. Emissão fiscal só faz sentido sobre venda fechada — em {@code OPEN}, ainda há
 * mutação possível; em {@code CANCELLED}, o estoque foi estornado.
 */
public class SaleNotInvoiceableException extends DomainException {

  public SaleNotInvoiceableException(String saleId) {
    super(
        "fiscal.sale-not-invoiceable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Venda não elegível para emissão fiscal",
        "Venda " + saleId + " não existe ou não está fechada — emissão fiscal recusada.");
  }
}
