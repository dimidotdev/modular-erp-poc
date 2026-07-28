package dev.dimi.erp.movimentacao.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code sales_order_item}. Interno ao módulo. */
public record SalesOrderItemId(@JsonValue UUID value) {

  public SalesOrderItemId {
    Objects.requireNonNull(value, "sales order item id é obrigatório");
  }

  public static SalesOrderItemId newId() {
    return new SalesOrderItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
