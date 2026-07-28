package dev.dimi.erp.produto.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code attribute_template}. */
public record AttributeTemplateId(@JsonValue UUID value) {

  public AttributeTemplateId {
    Objects.requireNonNull(value, "attribute template id é obrigatório");
  }

  public static AttributeTemplateId newId() {
    return new AttributeTemplateId(UuidCreator.getTimeOrderedEpoch());
  }
}
