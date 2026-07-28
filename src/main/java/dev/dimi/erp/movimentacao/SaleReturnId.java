package dev.dimi.erp.movimentacao;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code sale_return} (devolução de venda). */
public record SaleReturnId(@JsonValue UUID value) {

  public SaleReturnId {
    Objects.requireNonNull(value, "sale return id é obrigatório");
  }

  public static SaleReturnId newId() {
    return new SaleReturnId(UuidCreator.getTimeOrderedEpoch());
  }

  public static SaleReturnId of(String raw) {
    return new SaleReturnId(UUID.fromString(raw));
  }
}
