package dev.dimi.erp.compras;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code purchase_request} (SC). */
public record PurchaseRequestId(@JsonValue UUID value) {

  public PurchaseRequestId {
    Objects.requireNonNull(value, "purchase request id é obrigatório");
  }

  public static PurchaseRequestId newId() {
    return new PurchaseRequestId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PurchaseRequestId of(String raw) {
    return new PurchaseRequestId(UUID.fromString(raw));
  }
}
