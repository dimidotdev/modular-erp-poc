package dev.dimi.erp.contratos;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * ID tipado para {@code billing_cycle} (ciclo de faturamento mensal do contrato). Serializa como
 * string UUID.
 */
public record BillingCycleId(@JsonValue UUID value) {

  public BillingCycleId {
    Objects.requireNonNull(value, "billing cycle id é obrigatório");
  }

  public static BillingCycleId newId() {
    return new BillingCycleId(UuidCreator.getTimeOrderedEpoch());
  }

  public static BillingCycleId of(String raw) {
    return new BillingCycleId(UUID.fromString(raw));
  }
}
