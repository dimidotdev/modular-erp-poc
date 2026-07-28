package dev.dimi.erp.movimentacao.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code sale_return_item}. */
public record SaleReturnItemId(@JsonValue UUID value) {

  public SaleReturnItemId {
    Objects.requireNonNull(value, "sale return item id é obrigatório");
  }

  public static SaleReturnItemId newId() {
    return new SaleReturnItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
