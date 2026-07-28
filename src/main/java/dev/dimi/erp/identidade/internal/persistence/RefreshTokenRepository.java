package dev.dimi.erp.identidade.internal.persistence;

import dev.dimi.erp.identidade.internal.domain.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** Todos os tokens da chain (para revogação em cascata após detecção de reuso). */
  List<RefreshToken> findByChainId(UUID chainId);

  /** Todos os tokens ativos do usuário (revogação de todas as sessões). */
  @Query(
      """
      SELECT r FROM RefreshToken r
      WHERE r.userId = :userId
        AND r.revokedAt IS NULL
        AND r.usedAt IS NULL
      """)
  List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId);

  /** Tokens da sessão (revogação ao logout específico). */
  @Query(
      """
      SELECT r FROM RefreshToken r
      WHERE r.sessionId = :sessionId
        AND r.revokedAt IS NULL
        AND r.usedAt IS NULL
      """)
  List<RefreshToken> findActiveBySessionId(@Param("sessionId") UUID sessionId);

  /** Revoga em massa por sessão (logout). */
  @Modifying
  @Query(
      """
      UPDATE RefreshToken r
      SET r.revokedAt = CURRENT_TIMESTAMP, r.revokedReason = :reason
      WHERE r.sessionId = :sessionId AND r.revokedAt IS NULL
      """)
  int revokeAllBySessionId(@Param("sessionId") UUID sessionId, @Param("reason") String reason);

  /** Revoga em massa por usuário (logout-all, troca de senha, suspeita). */
  @Modifying
  @Query(
      """
      UPDATE RefreshToken r
      SET r.revokedAt = CURRENT_TIMESTAMP, r.revokedReason = :reason
      WHERE r.userId = :userId AND r.revokedAt IS NULL
      """)
  int revokeAllByUserId(@Param("userId") UUID userId, @Param("reason") String reason);

  /** Revoga em massa por chain (após detecção de reuso). */
  @Modifying
  @Query(
      """
      UPDATE RefreshToken r
      SET r.revokedAt = CURRENT_TIMESTAMP, r.revokedReason = :reason
      WHERE r.chainId = :chainId AND r.revokedAt IS NULL
      """)
  int revokeAllByChainId(@Param("chainId") UUID chainId, @Param("reason") String reason);
}
