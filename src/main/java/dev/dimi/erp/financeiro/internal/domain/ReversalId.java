package dev.dimi.erp.financeiro.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code financial_reversal}. */
public record ReversalId(@JsonValue UUID value) {

  public ReversalId {
    Objects.requireNonNull(value, "reversal id é obrigatório");
  }

  public static ReversalId newId() {
    return new ReversalId(UuidCreator.getTimeOrderedEpoch());
  }
}
