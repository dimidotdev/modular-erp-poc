package dev.dimi.erp.identidade;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code role}. Serializa como string UUID. */
public record RoleId(@JsonValue UUID value) {

  public RoleId {
    Objects.requireNonNull(value, "role id é obrigatório");
  }

  public static RoleId newId() {
    return new RoleId(UuidCreator.getTimeOrderedEpoch());
  }

  public static RoleId of(String raw) {
    return new RoleId(UUID.fromString(raw));
  }
}
