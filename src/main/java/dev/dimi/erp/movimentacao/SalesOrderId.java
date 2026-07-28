package dev.dimi.erp.movimentacao;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code sales_order} (pedido de venda). */
public record SalesOrderId(@JsonValue UUID value) {

  public SalesOrderId {
    Objects.requireNonNull(value, "sales order id é obrigatório");
  }

  public static SalesOrderId newId() {
    return new SalesOrderId(UuidCreator.getTimeOrderedEpoch());
  }

  public static SalesOrderId of(String raw) {
    return new SalesOrderId(UUID.fromString(raw));
  }
}
