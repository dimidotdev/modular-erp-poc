package dev.dimi.erp.financeiro;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code cost_center} — centro de custo. */
public record CostCenterId(@JsonValue UUID value) {

  public CostCenterId {
    Objects.requireNonNull(value, "cost center id é obrigatório");
  }

  public static CostCenterId newId() {
    return new CostCenterId(UuidCreator.getTimeOrderedEpoch());
  }

  public static CostCenterId of(String raw) {
    return new CostCenterId(UUID.fromString(raw));
  }
}
