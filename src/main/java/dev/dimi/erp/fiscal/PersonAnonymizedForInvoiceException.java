package dev.dimi.erp.fiscal;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de emitir NFe para uma pessoa que foi anonimizada por LGPD (RN-CAD-011) — {@code
 * PersonView.document() == null} ou {@code PersonView.anonymized() == true}. A NFe exige CPF/CNPJ
 * do destinatário, então a emissão é recusada.
 *
 * <p>Idempotência por origem cobre o caso normal: invoices autorizadas antes da anonimização
 * permanecem válidas — esta exceção só surge se uma venda for fechada com customer já anonimizado e
 * tentar emissão depois.
 */
public class PersonAnonymizedForInvoiceException extends DomainException {

  public PersonAnonymizedForInvoiceException(String personId) {
    super(
        "fiscal.person-anonymized",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Cliente anonimizado",
        "Cliente "
            + personId
            + " foi anonimizado por LGPD — emissão fiscal recusada por exigir CPF/CNPJ.");
  }
}
