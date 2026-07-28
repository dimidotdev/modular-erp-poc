package dev.dimi.erp.estoque;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code stock_movement}. */
public record MovementId(@JsonValue UUID value) {

  public MovementId {
    Objects.requireNonNull(value, "movement id é obrigatório");
  }

  public static MovementId newId() {
    return new MovementId(UuidCreator.getTimeOrderedEpoch());
  }

  public static MovementId of(String raw) {
    return new MovementId(UUID.fromString(raw));
  }
}
