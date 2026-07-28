package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link QuotationId} não encontrou cotação. */
public class QuotationNotFoundException extends DomainException {

  private final QuotationId quotationId;

  public QuotationNotFoundException(QuotationId quotationId) {
    super(
        "compras.quotation-not-found",
        HttpStatus.NOT_FOUND,
        "Cotação não encontrada",
        "Não existe cotação com este id neste tenant.");
    this.quotationId = quotationId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("quotationId", quotationId.value().toString());
  }
}
