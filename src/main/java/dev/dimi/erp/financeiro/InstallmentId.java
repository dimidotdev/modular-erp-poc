package dev.dimi.erp.financeiro;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code financial_installment} — parcela de uma MVF. */
public record InstallmentId(@JsonValue UUID value) {

  public InstallmentId {
    Objects.requireNonNull(value, "installment id é obrigatório");
  }

  public static InstallmentId newId() {
    return new InstallmentId(UuidCreator.getTimeOrderedEpoch());
  }

  public static InstallmentId of(String raw) {
    return new InstallmentId(UUID.fromString(raw));
  }
}
