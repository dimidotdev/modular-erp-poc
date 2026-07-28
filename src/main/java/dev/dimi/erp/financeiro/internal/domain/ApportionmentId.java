package dev.dimi.erp.financeiro.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code financial_apportionment}. */
public record ApportionmentId(@JsonValue UUID value) {

  public ApportionmentId {
    Objects.requireNonNull(value, "apportionment id é obrigatório");
  }

  public static ApportionmentId newId() {
    return new ApportionmentId(UuidCreator.getTimeOrderedEpoch());
  }
}
