package dev.dimi.erp.movimentacao.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code sale_item}. Interno ao módulo. */
public record SaleItemId(@JsonValue UUID value) {

  public SaleItemId {
    Objects.requireNonNull(value, "sale item id é obrigatório");
  }

  public static SaleItemId newId() {
    return new SaleItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
