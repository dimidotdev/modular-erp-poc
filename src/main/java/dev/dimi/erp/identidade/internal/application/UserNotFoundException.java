package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Operação administrativa apontou para um {@code userId} que não existe no tenant atual. */
public class UserNotFoundException extends DomainException {

  private final UserId userId;

  public UserNotFoundException(UserId userId) {
    super(
        "identidade.user-not-found",
        HttpStatus.NOT_FOUND,
        "Usuário não encontrado",
        "Não existe usuário com este id neste tenant.");
    this.userId = userId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("userId", userId.value().toString());
  }
}
