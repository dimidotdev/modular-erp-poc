package dev.dimi.erp.cadastros;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code person_contact}. Serializa como string UUID. */
public record PersonContactId(@JsonValue UUID value) {

  public PersonContactId {
    Objects.requireNonNull(value, "person contact id é obrigatório");
  }

  public static PersonContactId newId() {
    return new PersonContactId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PersonContactId of(String raw) {
    return new PersonContactId(UUID.fromString(raw));
  }
}
