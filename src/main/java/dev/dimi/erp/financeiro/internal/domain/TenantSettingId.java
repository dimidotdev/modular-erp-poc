package dev.dimi.erp.financeiro.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code tenant_setting}. */
public record TenantSettingId(@JsonValue UUID value) {

  public TenantSettingId {
    Objects.requireNonNull(value, "tenant setting id é obrigatório");
  }

  public static TenantSettingId newId() {
    return new TenantSettingId(UuidCreator.getTimeOrderedEpoch());
  }
}
