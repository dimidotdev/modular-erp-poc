package dev.dimi.erp.identidade.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
 * Registro append-only de cada tentativa de login (sucesso ou falha).
 *
 * <p>Tabela particionada por dia em {@code attempted_at} (retenção 90 dias). Usada para detecção de
 * força bruta, alertas e investigação forense. {@code userId} é {@code null} quando o {@code
 * username} informado não corresponde a um usuário existente (anti-enumeração).
 */
@Entity
@Table(name = "login_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "username", "success"})
public class LoginAttempt {

  @EmbeddedId private LoginAttemptId id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "ip", nullable = false, columnDefinition = "inet")
  @JdbcTypeCode(SqlTypes.INET)
  private String ip;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "failure_reason")
  private String failureReason;

  public static LoginAttempt success(UUID userId, String username, String ip) {
    LoginAttempt a = newInstance(userId, username, ip);
    a.success = true;
    return a;
  }

  public static LoginAttempt failure(UUID userId, String username, String ip, String reason) {
    LoginAttempt a = newInstance(userId, username, ip);
    a.success = false;
    a.failureReason = reason;
    return a;
  }

  private static LoginAttempt newInstance(UUID userId, String username, String ip) {
    Instant now = Instant.now();
    LoginAttempt a = new LoginAttempt();
    a.id = new LoginAttemptId(UuidCreator.getTimeOrderedEpoch(), now);
    a.userId = userId;
    a.username = Objects.requireNonNull(username);
    a.ip = Objects.requireNonNull(ip);
    return a;
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      id = new LoginAttemptId(UuidCreator.getTimeOrderedEpoch(), Instant.now());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LoginAttempt other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
