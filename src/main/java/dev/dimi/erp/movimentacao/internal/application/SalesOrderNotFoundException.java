package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link SalesOrderId} não encontrou pedido no tenant. */
public class SalesOrderNotFoundException extends DomainException {

  private final SalesOrderId salesOrderId;

  public SalesOrderNotFoundException(SalesOrderId salesOrderId) {
    super(
        "movimentacao.sales-order-not-found",
        HttpStatus.NOT_FOUND,
        "Pedido não encontrado",
        "Não existe pedido com este id neste tenant.");
    this.salesOrderId = salesOrderId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("salesOrderId", salesOrderId.value().toString());
  }
}
