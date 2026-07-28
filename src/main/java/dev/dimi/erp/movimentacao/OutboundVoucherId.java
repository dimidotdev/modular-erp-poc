package dev.dimi.erp.movimentacao;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code outbound_voucher} (saída avulsa — amostra, brinde, garantia, perda). */
public record OutboundVoucherId(@JsonValue UUID value) {

  public OutboundVoucherId {
    Objects.requireNonNull(value, "outbound voucher id é obrigatório");
  }

  public static OutboundVoucherId newId() {
    return new OutboundVoucherId(UuidCreator.getTimeOrderedEpoch());
  }

  public static OutboundVoucherId of(String raw) {
    return new OutboundVoucherId(UUID.fromString(raw));
  }
}
