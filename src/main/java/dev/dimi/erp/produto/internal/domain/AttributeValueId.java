package dev.dimi.erp.produto.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code attribute_value}. */
public record AttributeValueId(@JsonValue UUID value) {

  public AttributeValueId {
    Objects.requireNonNull(value, "attribute value id é obrigatório");
  }

  public static AttributeValueId newId() {
    return new AttributeValueId(UuidCreator.getTimeOrderedEpoch());
  }
}
