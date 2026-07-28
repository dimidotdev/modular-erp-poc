package dev.dimi.erp.ordemservico;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code service_order_product} — linha de produto consumida em uma OS. */
public record OsProductId(@JsonValue UUID value) {

  public OsProductId {
    Objects.requireNonNull(value, "os product id é obrigatório");
  }

  public static OsProductId newId() {
    return new OsProductId(UuidCreator.getTimeOrderedEpoch());
  }

  public static OsProductId of(String raw) {
    return new OsProductId(UUID.fromString(raw));
  }
}
