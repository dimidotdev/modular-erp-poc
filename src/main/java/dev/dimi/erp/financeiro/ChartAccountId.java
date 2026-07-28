package dev.dimi.erp.financeiro;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code chart_account} — conta do plano de contas. */
public record ChartAccountId(@JsonValue UUID value) {

  public ChartAccountId {
    Objects.requireNonNull(value, "chart account id é obrigatório");
  }

  public static ChartAccountId newId() {
    return new ChartAccountId(UuidCreator.getTimeOrderedEpoch());
  }

  public static ChartAccountId of(String raw) {
    return new ChartAccountId(UUID.fromString(raw));
  }
}
