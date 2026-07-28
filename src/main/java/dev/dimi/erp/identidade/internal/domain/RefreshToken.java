package dev.dimi.erp.identidade.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Refresh token armazenado por hash SHA-256 (nunca o valor cru).
 *
 * <p>Rotação one-shot: cada uso marca {@code usedAt} e emite token novo na mesma {@code chainId}.
 * Tentativa de uso de token já marcado {@code usedAt} indica comprometimento → revoga toda a chain
 * via service.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "sessionId", "chainId"})
public class RefreshToken {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "chain_id", nullable = false, updatable = false)
  private UUID chainId;

  @Column(name = "token_hash", nullable = false, unique = true, updatable = false)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_reason")
  private String revokedReason;

  @Column(name = "user_agent")
  private String userAgent;

  @Column(name = "ip", columnDefinition = "inet")
  @JdbcTypeCode(SqlTypes.INET)
  private String ip;

  /** Primeira emissão do token na chain (login). */
  public static RefreshToken issueNew(
      UserId userId,
      SessionId sessionId,
      String tokenHash,
      Instant expiresAt,
      String userAgent,
      String ip) {
    RefreshToken t = new RefreshToken();
    t.id = UuidCreator.getTimeOrderedEpoch();
    t.userId = userId.value();
    t.sessionId = sessionId.value();
    t.chainId = t.id; // primeira da chain
    t.tokenHash = Objects.requireNonNull(tokenHash);
    t.issuedAt = Instant.now();
    t.expiresAt = Objects.requireNonNull(expiresAt);
    t.userAgent = userAgent;
    t.ip = ip;
    return t;
  }

  /** Sucessor na mesma chain (refresh). Mantém {@code sessionId} e {@code chainId} do parent. */
  public static RefreshToken rotateFrom(
      RefreshToken parent, String tokenHash, Instant expiresAt, String userAgent, String ip) {
    RefreshToken t = new RefreshToken();
    t.id = UuidCreator.getTimeOrderedEpoch();
    t.userId = parent.userId;
    t.sessionId = parent.sessionId;
    t.chainId = parent.chainId;
    t.tokenHash = Objects.requireNonNull(tokenHash);
    t.issuedAt = Instant.now();
    t.expiresAt = Objects.requireNonNull(expiresAt);
    t.userAgent = userAgent;
    t.ip = ip;
    return t;
  }

  public UserId user() {
    return new UserId(userId);
  }

  public SessionId session() {
    return new SessionId(sessionId);
  }

  public boolean isActive(Instant now) {
    return revokedAt == null && usedAt == null && now.isBefore(expiresAt);
  }

  /** Marca o token como consumido (após rotação). Idempotente. */
  public void markUsed() {
    if (usedAt == null) {
      usedAt = Instant.now();
    }
  }

  public void revoke(String reason) {
    if (revokedAt == null) {
      revokedAt = Instant.now();
      revokedReason = reason;
    }
  }

  public boolean wasUsed() {
    return usedAt != null;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (chainId == null) {
      chainId = id;
    }
    if (issuedAt == null) {
      issuedAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RefreshToken other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
