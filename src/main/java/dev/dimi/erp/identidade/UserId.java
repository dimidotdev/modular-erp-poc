package dev.dimi.erp.identidade;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code user_account}. Serializa como string UUID. */
public record UserId(@JsonValue UUID value) {

  public UserId {
    Objects.requireNonNull(value, "user id é obrigatório");
  }

  /** Gera novo {@link UserId} usando UUID v7 ordenado no tempo. */
  public static UserId newId() {
    return new UserId(UuidCreator.getTimeOrderedEpoch());
  }

  /** Constrói a partir de String UUID (uso interno em deserialização explícita). */
  public static UserId of(String raw) {
    return new UserId(UUID.fromString(raw));
  }
}
