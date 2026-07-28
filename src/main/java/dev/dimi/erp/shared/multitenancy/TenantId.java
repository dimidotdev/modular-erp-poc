package dev.dimi.erp.shared.multitenancy;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code shared.tenant}. Serializa como string UUID. */
public record TenantId(@JsonValue UUID value) {

  public TenantId {
    Objects.requireNonNull(value, "tenant id é obrigatório");
  }

  public static TenantId newId() {
    return new TenantId(UuidCreator.getTimeOrderedEpoch());
  }

  public static TenantId of(String raw) {
    return new TenantId(UUID.fromString(raw));
  }
}
