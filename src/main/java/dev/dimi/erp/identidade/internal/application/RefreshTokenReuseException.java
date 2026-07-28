package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de uso de refresh token já consumido (marcado {@code used_at}).
 *
 * <p>Indica forte suspeita de comprometimento — alguém roubou o token e o usou após o legítimo dono
 * já ter rotacionado. Política: revogar TODA a chain (todos os tokens com o mesmo {@code
 * chain_id}), forçando o atacante e o usuário legítimo a reautenticar.
 *
 * <p>Apresenta-se ao cliente como 401 com mensagem genérica — não revela que houve detecção
 * (decisão de segurança: não alertar o atacante de que foi pego). Audit log via {@code
 * SessionRevokedAllEvent} cuida do alerta interno.
 */
public class RefreshTokenReuseException extends DomainException {

  public RefreshTokenReuseException() {
    super(
        "identidade.invalid-refresh-token",
        HttpStatus.UNAUTHORIZED,
        "Refresh token inválido",
        "O refresh token apresentado é inválido. Reautentique-se.");
  }
}
