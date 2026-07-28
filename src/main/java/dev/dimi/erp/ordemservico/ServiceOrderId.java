package dev.dimi.erp.ordemservico;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code service_order}. */
public record ServiceOrderId(@JsonValue UUID value) {

  public ServiceOrderId {
    Objects.requireNonNull(value, "service order id é obrigatório");
  }

  public static ServiceOrderId newId() {
    return new ServiceOrderId(UuidCreator.getTimeOrderedEpoch());
  }

  public static ServiceOrderId of(String raw) {
    return new ServiceOrderId(UUID.fromString(raw));
  }
}
