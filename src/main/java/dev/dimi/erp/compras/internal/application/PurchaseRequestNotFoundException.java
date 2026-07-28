package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link PurchaseRequestId} não encontrou SC no tenant. */
public class PurchaseRequestNotFoundException extends DomainException {

  private final PurchaseRequestId requestId;

  public PurchaseRequestNotFoundException(PurchaseRequestId requestId) {
    super(
        "compras.request-not-found",
        HttpStatus.NOT_FOUND,
        "Solicitação de compra não encontrada",
        "Não existe SC com este id neste tenant.");
    this.requestId = requestId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("requestId", requestId.value().toString());
  }
}
