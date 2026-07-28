package dev.dimi.erp.produto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code catalog_brand}. */
public record BrandId(@JsonValue UUID value) {

  public BrandId {
    Objects.requireNonNull(value, "brand id é obrigatório");
  }

  public static BrandId newId() {
    return new BrandId(UuidCreator.getTimeOrderedEpoch());
  }

  public static BrandId of(String raw) {
    return new BrandId(UUID.fromString(raw));
  }
}
