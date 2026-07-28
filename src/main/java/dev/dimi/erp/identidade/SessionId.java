package dev.dimi.erp.identidade;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * ID tipado para sessão de autenticação. Aparece no claim {@code sid} do JWT, em logs e
 * correlaciona todos os refresh tokens de uma mesma chain.
 */
public record SessionId(@JsonValue UUID value) {

  public SessionId {
    Objects.requireNonNull(value, "session id é obrigatório");
  }

  public static SessionId newId() {
    return new SessionId(UuidCreator.getTimeOrderedEpoch());
  }

  public static SessionId of(String raw) {
    return new SessionId(UUID.fromString(raw));
  }
}
