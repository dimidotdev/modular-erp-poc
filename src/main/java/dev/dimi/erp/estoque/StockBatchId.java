package dev.dimi.erp.estoque;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code stock_batch} (lote opcional com validade). */
public record StockBatchId(@JsonValue UUID value) {

  public StockBatchId {
    Objects.requireNonNull(value, "stock batch id é obrigatório");
  }

  public static StockBatchId newId() {
    return new StockBatchId(UuidCreator.getTimeOrderedEpoch());
  }

  public static StockBatchId of(String raw) {
    return new StockBatchId(UUID.fromString(raw));
  }
}
