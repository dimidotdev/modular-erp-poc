package dev.dimi.erp.compras;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code purchase_order} (PC). */
public record PurchaseOrderId(@JsonValue UUID value) {

  public PurchaseOrderId {
    Objects.requireNonNull(value, "purchase order id é obrigatório");
  }

  public static PurchaseOrderId newId() {
    return new PurchaseOrderId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PurchaseOrderId of(String raw) {
    return new PurchaseOrderId(UUID.fromString(raw));
  }
}
