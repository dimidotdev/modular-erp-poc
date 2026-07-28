package dev.dimi.erp.financeiro.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code financial_payment_application}. */
public record PaymentApplicationId(@JsonValue UUID value) {

  public PaymentApplicationId {
    Objects.requireNonNull(value, "payment application id é obrigatório");
  }

  public static PaymentApplicationId newId() {
    return new PaymentApplicationId(UuidCreator.getTimeOrderedEpoch());
  }
}
