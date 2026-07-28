package dev.dimi.erp.cadastros;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code person_address}. Serializa como string UUID. */
public record PersonAddressId(@JsonValue UUID value) {

  public PersonAddressId {
    Objects.requireNonNull(value, "person address id é obrigatório");
  }

  public static PersonAddressId newId() {
    return new PersonAddressId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PersonAddressId of(String raw) {
    return new PersonAddressId(UUID.fromString(raw));
  }
}
