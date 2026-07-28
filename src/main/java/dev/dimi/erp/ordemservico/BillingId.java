package dev.dimi.erp.ordemservico;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code service_order_billing}. */
public record BillingId(@JsonValue UUID value) {

  public BillingId {
    Objects.requireNonNull(value, "billing id é obrigatório");
  }

  public static BillingId newId() {
    return new BillingId(UuidCreator.getTimeOrderedEpoch());
  }

  public static BillingId of(String raw) {
    return new BillingId(UUID.fromString(raw));
  }
}
