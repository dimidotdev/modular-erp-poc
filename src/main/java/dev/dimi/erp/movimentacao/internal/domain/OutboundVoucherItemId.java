package dev.dimi.erp.movimentacao.internal.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code outbound_voucher_item}. */
public record OutboundVoucherItemId(@JsonValue UUID value) {

  public OutboundVoucherItemId {
    Objects.requireNonNull(value, "outbound voucher item id é obrigatório");
  }

  public static OutboundVoucherItemId newId() {
    return new OutboundVoucherItemId(UuidCreator.getTimeOrderedEpoch());
  }
}
