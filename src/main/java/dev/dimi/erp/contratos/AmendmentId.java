package dev.dimi.erp.contratos;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code contract_amendment} (aditivo contratual). Serializa como string UUID. */
public record AmendmentId(@JsonValue UUID value) {

  public AmendmentId {
    Objects.requireNonNull(value, "amendment id é obrigatório");
  }

  public static AmendmentId newId() {
    return new AmendmentId(UuidCreator.getTimeOrderedEpoch());
  }

  public static AmendmentId of(String raw) {
    return new AmendmentId(UUID.fromString(raw));
  }
}
