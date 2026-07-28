package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Refresh token apresentado é inválido (formato errado, não existe, já revogado, expirado, ou
 * usuário não está mais ativo).
 *
 * <p>Mensagem genérica deliberada — não revela o motivo específico para o cliente, evitando
 * enumeração ou inferência. Cliente deve forçar reautenticação completa.
 *
 * <p>Diferente de {@link RefreshTokenReuseException}, que indica suspeita de comprometimento e
 * dispara revogação em cascata da chain inteira.
 */
public class InvalidRefreshTokenException extends DomainException {

  public InvalidRefreshTokenException() {
    super(
        "identidade.invalid-refresh-token",
        HttpStatus.UNAUTHORIZED,
        "Refresh token inválido",
        "O refresh token apresentado é inválido. Reautentique-se.");
  }
}
