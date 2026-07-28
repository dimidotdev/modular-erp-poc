package dev.dimi.erp.produto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code price_table}. */
public record PriceTableId(@JsonValue UUID value) {

  public PriceTableId {
    Objects.requireNonNull(value, "price table id é obrigatório");
  }

  public static PriceTableId newId() {
    return new PriceTableId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PriceTableId of(String raw) {
    return new PriceTableId(UUID.fromString(raw));
  }
}
