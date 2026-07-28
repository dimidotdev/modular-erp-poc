package dev.dimi.erp.compras;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code purchase_receipt} (recebimento físico). */
public record PurchaseReceiptId(@JsonValue UUID value) {

  public PurchaseReceiptId {
    Objects.requireNonNull(value, "purchase receipt id é obrigatório");
  }

  public static PurchaseReceiptId newId() {
    return new PurchaseReceiptId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PurchaseReceiptId of(String raw) {
    return new PurchaseReceiptId(UUID.fromString(raw));
  }
}
