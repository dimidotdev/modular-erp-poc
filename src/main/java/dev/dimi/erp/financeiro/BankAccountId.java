package dev.dimi.erp.financeiro;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code bank_account} — conta bancária (caixa/banco/cartão/gateway/voucher). */
public record BankAccountId(@JsonValue UUID value) {

  public BankAccountId {
    Objects.requireNonNull(value, "bank account id é obrigatório");
  }

  public static BankAccountId newId() {
    return new BankAccountId(UuidCreator.getTimeOrderedEpoch());
  }

  public static BankAccountId of(String raw) {
    return new BankAccountId(UUID.fromString(raw));
  }
}
