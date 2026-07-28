package dev.dimi.erp.estoque;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code stock_location}. */
public record StockLocationId(@JsonValue UUID value) {

  public StockLocationId {
    Objects.requireNonNull(value, "stock location id é obrigatório");
  }

  public static StockLocationId newId() {
    return new StockLocationId(UuidCreator.getTimeOrderedEpoch());
  }

  public static StockLocationId of(String raw) {
    return new StockLocationId(UUID.fromString(raw));
  }
}
