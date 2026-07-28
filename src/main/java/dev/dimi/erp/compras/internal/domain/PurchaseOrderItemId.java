package dev.dimi.erp.compras.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code purchase_order_item}. */
public record PurchaseOrderItemId(@JsonValue UUID value) {

  public PurchaseOrderItemId {
    Objects.requireNonNull(value, "purchase order item id é obrigatório");
  }

  public static PurchaseOrderItemId newId() {
    return new PurchaseOrderItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
