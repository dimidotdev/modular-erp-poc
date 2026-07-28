package dev.dimi.erp.fiscal;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code invoice_series} — série fiscal por filial+modelo. */
public record InvoiceSeriesId(@JsonValue UUID value) {

  public InvoiceSeriesId {
    Objects.requireNonNull(value, "invoice series id é obrigatório");
  }

  public static InvoiceSeriesId newId() {
    return new InvoiceSeriesId(UuidCreator.getTimeOrderedEpoch());
  }

  public static InvoiceSeriesId of(String raw) {
    return new InvoiceSeriesId(UUID.fromString(raw));
  }
}
