package dev.dimi.erp.movimentacao;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code stock_transfer} (transferência entre filiais/locais). */
public record StockTransferId(@JsonValue UUID value) {

  public StockTransferId {
    Objects.requireNonNull(value, "stock transfer id é obrigatório");
  }

  public static StockTransferId newId() {
    return new StockTransferId(UuidCreator.getTimeOrderedEpoch());
  }

  public static StockTransferId of(String raw) {
    return new StockTransferId(UUID.fromString(raw));
  }
}
