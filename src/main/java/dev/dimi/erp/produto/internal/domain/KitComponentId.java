package dev.dimi.erp.produto.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code product_kit_component}. */
public record KitComponentId(@JsonValue UUID value) {

  public KitComponentId {
    Objects.requireNonNull(value, "kit component id é obrigatório");
  }

  public static KitComponentId newId() {
    return new KitComponentId(UuidCreator.getTimeOrderedEpoch());
  }
}
