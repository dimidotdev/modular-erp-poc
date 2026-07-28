package dev.dimi.erp.compras;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code quotation}. */
public record QuotationId(@JsonValue UUID value) {

  public QuotationId {
    Objects.requireNonNull(value, "quotation id é obrigatório");
  }

  public static QuotationId newId() {
    return new QuotationId(UuidCreator.getTimeOrderedEpoch());
  }

  public static QuotationId of(String raw) {
    return new QuotationId(UUID.fromString(raw));
  }
}
