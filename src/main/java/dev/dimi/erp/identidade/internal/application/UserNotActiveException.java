package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Usuário existe e senha confere mas está SUSPENDED ou DEACTIVATED.
 *
 * <p>Diferente de {@link InvalidCredentialsException}: aqui o cliente apresentou credenciais
 * corretas, então faz sentido informar o estado da conta para que ele saiba que precisa contatar um
 * admin (UX legítima). Retorna 403, não 401.
 */
public class UserNotActiveException extends DomainException {

  public UserNotActiveException(String status) {
    super(
        "identidade.user-not-active",
        HttpStatus.FORBIDDEN,
        "Conta de usuário não está ativa",
        "Esta conta está com status '" + status + "'. Contate um administrador do tenant.");
  }
}
