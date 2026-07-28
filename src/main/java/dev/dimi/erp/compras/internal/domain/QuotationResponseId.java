package dev.dimi.erp.compras.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code quotation_response}. */
public record QuotationResponseId(@JsonValue UUID value) {

  public QuotationResponseId {
    Objects.requireNonNull(value, "quotation response id é obrigatório");
  }

  public static QuotationResponseId newId() {
    return new QuotationResponseId(UuidCreator.getTimeOrderedEpoch());
  }
}
