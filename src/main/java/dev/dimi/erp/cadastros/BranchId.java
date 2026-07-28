package dev.dimi.erp.cadastros;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code branch}. Serializa como string UUID. */
public record BranchId(@JsonValue UUID value) {

  public BranchId {
    Objects.requireNonNull(value, "branch id é obrigatório");
  }

  public static BranchId newId() {
    return new BranchId(UuidCreator.getTimeOrderedEpoch());
  }

  public static BranchId of(String raw) {
    return new BranchId(UUID.fromString(raw));
  }
}
