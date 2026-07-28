package dev.dimi.erp.financeiro;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code financial_payment} — pagamento (registro de quitação). */
public record PaymentId(@JsonValue UUID value) {

  public PaymentId {
    Objects.requireNonNull(value, "payment id é obrigatório");
  }

  public static PaymentId newId() {
    return new PaymentId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PaymentId of(String raw) {
    return new PaymentId(UUID.fromString(raw));
  }
}
