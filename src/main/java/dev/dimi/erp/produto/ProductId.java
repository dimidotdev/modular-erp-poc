package dev.dimi.erp.produto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code product}. Serializa como string UUID. */
public record ProductId(@JsonValue UUID value) {

  public ProductId {
    Objects.requireNonNull(value, "product id é obrigatório");
  }

  public static ProductId newId() {
    return new ProductId(UuidCreator.getTimeOrderedEpoch());
  }

  public static ProductId of(String raw) {
    return new ProductId(UUID.fromString(raw));
  }
}
