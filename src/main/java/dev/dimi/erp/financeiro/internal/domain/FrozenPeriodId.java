package dev.dimi.erp.financeiro.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code frozen_period}. */
public record FrozenPeriodId(@JsonValue UUID value) {

  public FrozenPeriodId {
    Objects.requireNonNull(value, "frozen period id é obrigatório");
  }

  public static FrozenPeriodId newId() {
    return new FrozenPeriodId(UuidCreator.getTimeOrderedEpoch());
  }
}
