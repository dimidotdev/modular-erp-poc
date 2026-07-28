package dev.dimi.erp.produto.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code variant}. Interno ao módulo produto. */
public record VariantId(@JsonValue UUID value) {

  public VariantId {
    Objects.requireNonNull(value, "variant id é obrigatório");
  }

  public static VariantId newId() {
    return new VariantId(UuidCreator.getTimeOrderedEpoch());
  }
}
