package dev.dimi.erp.contratos;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code contract_adjustment} (reajuste por índice). Serializa como string UUID. */
public record AdjustmentId(@JsonValue UUID value) {

  public AdjustmentId {
    Objects.requireNonNull(value, "adjustment id é obrigatório");
  }

  public static AdjustmentId newId() {
    return new AdjustmentId(UuidCreator.getTimeOrderedEpoch());
  }

  public static AdjustmentId of(String raw) {
    return new AdjustmentId(UUID.fromString(raw));
  }
}
