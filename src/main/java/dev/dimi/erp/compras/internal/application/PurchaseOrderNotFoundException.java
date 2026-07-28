package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link PurchaseOrderId} não encontrou PC. */
public class PurchaseOrderNotFoundException extends DomainException {

  private final PurchaseOrderId orderId;

  public PurchaseOrderNotFoundException(PurchaseOrderId orderId) {
    super(
        "compras.order-not-found",
        HttpStatus.NOT_FOUND,
        "Pedido de compra não encontrado",
        "Não existe PC com este id neste tenant.");
    this.orderId = orderId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("orderId", orderId.value().toString());
  }
}
