package dev.dimi.erp.compras.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code supplier_return_item}. */
public record SupplierReturnItemId(@JsonValue UUID value) {

  public SupplierReturnItemId {
    Objects.requireNonNull(value, "supplier return item id é obrigatório");
  }

  public static SupplierReturnItemId newId() {
    return new SupplierReturnItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
