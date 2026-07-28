package dev.dimi.erp.fiscal.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code fiscal_event}. */
public record FiscalEventId(@JsonValue UUID value) {

  public FiscalEventId {
    Objects.requireNonNull(value, "fiscal event id é obrigatório");
  }

  public static FiscalEventId newId() {
    return new FiscalEventId(UuidCreator.getTimeOrderedEpoch());
  }
}
