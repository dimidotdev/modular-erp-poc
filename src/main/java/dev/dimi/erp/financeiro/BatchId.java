package dev.dimi.erp.financeiro;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * ID tipado para {@code (batch_id de financial_payment)} — lote de pagamento — 1 POST batch pode
 * gerar N rows.
 */
public record BatchId(@JsonValue UUID value) {

  public BatchId {
    Objects.requireNonNull(value, "batch id é obrigatório");
  }

  public static BatchId newId() {
    return new BatchId(UuidCreator.getTimeOrderedEpoch());
  }

  public static BatchId of(String raw) {
    return new BatchId(UUID.fromString(raw));
  }
}
