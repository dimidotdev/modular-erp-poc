package dev.dimi.erp.estoque;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * ID tipado para {@code stock_snapshot} — o saldo agregado por (packaging, branch, location,
 * batch). Cada linha tem um UUID próprio para facilitar atualização e lock pessimista.
 */
public record StockSnapshotId(@JsonValue UUID value) {

  public StockSnapshotId {
    Objects.requireNonNull(value, "stock snapshot id é obrigatório");
  }

  public static StockSnapshotId newId() {
    return new StockSnapshotId(UuidCreator.getTimeOrderedEpoch());
  }

  public static StockSnapshotId of(String raw) {
    return new StockSnapshotId(UUID.fromString(raw));
  }
}
