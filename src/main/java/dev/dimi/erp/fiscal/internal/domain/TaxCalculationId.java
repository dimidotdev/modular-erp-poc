package dev.dimi.erp.fiscal.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code tax_calculation}. */
public record TaxCalculationId(@JsonValue UUID value) {

  public TaxCalculationId {
    Objects.requireNonNull(value, "tax calculation id é obrigatório");
  }

  public static TaxCalculationId newId() {
    return new TaxCalculationId(UuidCreator.getTimeOrderedEpoch());
  }
}
