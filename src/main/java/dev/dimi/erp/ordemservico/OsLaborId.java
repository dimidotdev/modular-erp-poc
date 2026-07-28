package dev.dimi.erp.ordemservico;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code service_order_labor} — apontamento de mão-de-obra em uma OS. */
public record OsLaborId(@JsonValue UUID value) {

  public OsLaborId {
    Objects.requireNonNull(value, "os labor id é obrigatório");
  }

  public static OsLaborId newId() {
    return new OsLaborId(UuidCreator.getTimeOrderedEpoch());
  }

  public static OsLaborId of(String raw) {
    return new OsLaborId(UUID.fromString(raw));
  }
}
