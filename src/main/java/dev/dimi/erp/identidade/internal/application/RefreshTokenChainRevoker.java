package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.SessionRevokedAllEvent;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.persistence.RefreshTokenRepository;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revogação de toda a chain de refresh tokens em transação SEPARADA da rotação.
 *
 * <p>Por que separar? Quando {@link RefreshTokenService} detecta reuse, ele lança {@link
 * RefreshTokenReuseException} para responder 401 ao cliente. Se a revogação acontecesse dentro da
 * mesma transação, o rollback automático causado pela exceção desfaria o {@code UPDATE} dos tokens
 * — e o atacante poderia simplesmente usar o próximo refresh válido da chain.
 *
 * <p>{@link Propagation#REQUIRES_NEW} garante que o UPDATE comita ANTES da exception do caller
 * propagar e disparar o rollback da outer transaction. Requer bean separado pois proxy AOP do
 * Spring não intercepta chamadas internas em {@code this.method()}.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenChainRevoker {

  private final RefreshTokenRepository refreshTokens;
  private final ApplicationEventPublisher events;

  /**
   * Revoga em massa todos os tokens da {@code chainId}, publica {@link SessionRevokedAllEvent}, e
   * retorna a contagem revogada para o caller logar.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int revokeChainOnReuse(
      UUID chainId,
      UUID userId,
      TenantId tenantId,
      String tenantSlug,
      String triggeringIp,
      Instant now) {
    int revoked = refreshTokens.revokeAllByChainId(chainId, "REUSE_DETECTED");
    events.publishEvent(
        new SessionRevokedAllEvent(
            new UserId(userId),
            tenantId,
            tenantSlug,
            "REUSE_DETECTED",
            revoked,
            triggeringIp,
            now));
    return revoked;
  }
}
