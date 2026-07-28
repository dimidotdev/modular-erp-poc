package dev.dimi.erp.cadastros;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code employee}. Serializa como string UUID. */
public record EmployeeId(@JsonValue UUID value) {

  public EmployeeId {
    Objects.requireNonNull(value, "employee id é obrigatório");
  }

  public static EmployeeId newId() {
    return new EmployeeId(UuidCreator.getTimeOrderedEpoch());
  }

  public static EmployeeId of(String raw) {
    return new EmployeeId(UUID.fromString(raw));
  }
}
