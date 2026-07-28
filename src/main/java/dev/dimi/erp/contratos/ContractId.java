package dev.dimi.erp.contratos;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code contract}. Serializa como string UUID. */
public record ContractId(@JsonValue UUID value) {

  public ContractId {
    Objects.requireNonNull(value, "contract id é obrigatório");
  }

  public static ContractId newId() {
    return new ContractId(UuidCreator.getTimeOrderedEpoch());
  }

  public static ContractId of(String raw) {
    return new ContractId(UUID.fromString(raw));
  }
}
