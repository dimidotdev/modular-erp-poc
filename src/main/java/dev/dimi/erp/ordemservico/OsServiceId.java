package dev.dimi.erp.ordemservico;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code service_order_service} — linha de serviço prestado em uma OS. */
public record OsServiceId(@JsonValue UUID value) {

  public OsServiceId {
    Objects.requireNonNull(value, "os service id é obrigatório");
  }

  public static OsServiceId newId() {
    return new OsServiceId(UuidCreator.getTimeOrderedEpoch());
  }

  public static OsServiceId of(String raw) {
    return new OsServiceId(UUID.fromString(raw));
  }
}
