package dev.dimi.erp.fiscal.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code invoice_item}. */
public record InvoiceItemId(@JsonValue UUID value) {

  public InvoiceItemId {
    Objects.requireNonNull(value, "invoice item id é obrigatório");
  }

  public static InvoiceItemId newId() {
    return new InvoiceItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
