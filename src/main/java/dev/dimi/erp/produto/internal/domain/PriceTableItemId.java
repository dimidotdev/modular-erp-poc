package dev.dimi.erp.produto.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code price_table_item}. */
public record PriceTableItemId(@JsonValue UUID value) {

  public PriceTableItemId {
    Objects.requireNonNull(value, "price table item id é obrigatório");
  }

  public static PriceTableItemId newId() {
    return new PriceTableItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
