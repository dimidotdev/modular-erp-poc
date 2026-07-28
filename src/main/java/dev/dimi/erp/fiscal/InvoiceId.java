package dev.dimi.erp.fiscal;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code invoice} — documento fiscal (NFE/NFCE/NFSE). */
public record InvoiceId(@JsonValue UUID value) {

  public InvoiceId {
    Objects.requireNonNull(value, "invoice id é obrigatório");
  }

  public static InvoiceId newId() {
    return new InvoiceId(UuidCreator.getTimeOrderedEpoch());
  }

  public static InvoiceId of(String raw) {
    return new InvoiceId(UUID.fromString(raw));
  }
}
