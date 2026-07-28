package dev.dimi.erp.financeiro;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code financial_record} — MVF (movimentação financeira). */
public record FinancialRecordId(@JsonValue UUID value) {

  public FinancialRecordId {
    Objects.requireNonNull(value, "financial record id é obrigatório");
  }

  public static FinancialRecordId newId() {
    return new FinancialRecordId(UuidCreator.getTimeOrderedEpoch());
  }

  public static FinancialRecordId of(String raw) {
    return new FinancialRecordId(UUID.fromString(raw));
  }
}
