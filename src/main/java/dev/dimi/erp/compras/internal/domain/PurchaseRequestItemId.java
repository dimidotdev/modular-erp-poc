package dev.dimi.erp.compras.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code purchase_request_item}. */
public record PurchaseRequestItemId(@JsonValue UUID value) {

  public PurchaseRequestItemId {
    Objects.requireNonNull(value, "purchase request item id é obrigatório");
  }

  public static PurchaseRequestItemId newId() {
    return new PurchaseRequestItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
