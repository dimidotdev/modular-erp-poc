package dev.dimi.erp.compras.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code purchase_receipt_item}. */
public record PurchaseReceiptItemId(@JsonValue UUID value) {

  public PurchaseReceiptItemId {
    Objects.requireNonNull(value, "purchase receipt item id é obrigatório");
  }

  public static PurchaseReceiptItemId newId() {
    return new PurchaseReceiptItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
