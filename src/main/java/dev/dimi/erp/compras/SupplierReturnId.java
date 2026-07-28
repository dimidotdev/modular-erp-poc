package dev.dimi.erp.compras;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code supplier_return} (devolução a fornecedor). */
public record SupplierReturnId(@JsonValue UUID value) {

  public SupplierReturnId {
    Objects.requireNonNull(value, "supplier return id é obrigatório");
  }

  public static SupplierReturnId newId() {
    return new SupplierReturnId(UuidCreator.getTimeOrderedEpoch());
  }

  public static SupplierReturnId of(String raw) {
    return new SupplierReturnId(UUID.fromString(raw));
  }
}
