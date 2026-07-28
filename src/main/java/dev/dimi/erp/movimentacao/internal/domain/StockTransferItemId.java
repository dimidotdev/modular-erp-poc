package dev.dimi.erp.movimentacao.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code stock_transfer_item}. */
public record StockTransferItemId(@JsonValue UUID value) {

  public StockTransferItemId {
    Objects.requireNonNull(value, "stock transfer item id é obrigatório");
  }

  public static StockTransferItemId newId() {
    return new StockTransferItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
