package dev.dimi.erp.movimentacao;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code sale} (venda — documento de saída efetiva). */
public record SaleId(@JsonValue UUID value) {

  public SaleId {
    Objects.requireNonNull(value, "sale id é obrigatório");
  }

  public static SaleId newId() {
    return new SaleId(UuidCreator.getTimeOrderedEpoch());
  }

  public static SaleId of(String raw) {
    return new SaleId(UUID.fromString(raw));
  }
}
