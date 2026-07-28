package dev.dimi.erp.produto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * ID tipado para {@code packaging} — o SKU que viaja entre módulos. Venda, NFe, compra e estoque
 * trabalham com {@code PackagingId} (RN-PRD-001), não com {@code ProductId}.
 */
public record PackagingId(@JsonValue UUID value) {

  public PackagingId {
    Objects.requireNonNull(value, "packaging id é obrigatório");
  }

  public static PackagingId newId() {
    return new PackagingId(UuidCreator.getTimeOrderedEpoch());
  }

  public static PackagingId of(String raw) {
    return new PackagingId(UUID.fromString(raw));
  }
}
