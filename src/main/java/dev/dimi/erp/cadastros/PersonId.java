package dev.dimi.erp.cadastros;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code person}. Serializa como string UUID. */
public record PersonId(@JsonValue UUID value) {

  public PersonId {
    Objects.requireNonNull(value, "person id é obrigatório");
  }

  public static PersonId newId() {
    return new PersonId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PersonId of(String raw) {
    return new PersonId(UUID.fromString(raw));
  }
}
