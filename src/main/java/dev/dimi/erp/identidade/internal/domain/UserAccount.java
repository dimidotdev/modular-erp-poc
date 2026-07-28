package dev.dimi.erp.identidade.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
 * Usuário cadastrado num tenant.
 *
 * <p>Senhas vivem em {@code passwordHash} (Argon2id) e nunca são serializadas em DTO. {@code
 * username} e {@code email} são case-insensitive (unique index em {@code LOWER(...)} no DB).
 *
 * <p>O record {@code personId} é referência soft (sem FK enforced) para {@code cadastros.person} —
 * vinculação opcional do usuário a uma pessoa cadastrada (ex: funcionário com login).
 */
@Entity
@Table(name = "user_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "username", "status"})
public class UserAccount {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "username", nullable = false)
  private String username;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private UserStatus status;

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "last_login_ip", columnDefinition = "inet")
  @JdbcTypeCode(SqlTypes.INET)
  private String lastLoginIp;

  @Column(name = "person_id")
  private UUID personId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deactivated_at")
  private Instant deactivatedAt;

  @Column(name = "deactivated_reason")
  private String deactivatedReason;

  // ------------------------------------------------------------------
  // Factory + comandos de domínio
  // ------------------------------------------------------------------

  /** Cria um novo usuário ACTIVE. {@code passwordHash} já deve estar em Argon2id. */
  public static UserAccount register(
      String username,
      String email,
      String fullName,
      String passwordHash,
      boolean mustChangePassword) {
    UserAccount u = new UserAccount();
    u.id = UuidCreator.getTimeOrderedEpoch();
    u.username = Objects.requireNonNull(username);
    u.email = Objects.requireNonNull(email);
    u.fullName = Objects.requireNonNull(fullName);
    u.passwordHash = Objects.requireNonNull(passwordHash);
    u.status = UserStatus.ACTIVE;
    u.mustChangePassword = mustChangePassword;
    return u;
  }

  public UserId id() {
    return new UserId(id);
  }

  public void changePassword(String newPasswordHash) {
    this.passwordHash = Objects.requireNonNull(newPasswordHash);
    this.mustChangePassword = false;
  }

  public void recordLogin(Instant when, String fromIp) {
    this.lastLoginAt = when;
    this.lastLoginIp = fromIp;
  }

  public void suspend() {
    if (status == UserStatus.DEACTIVATED) {
      throw new IllegalStateException("usuário já desativado não pode ser suspenso");
    }
    this.status = UserStatus.SUSPENDED;
  }

  public void reactivate() {
    if (status == UserStatus.DEACTIVATED) {
      throw new IllegalStateException("usuário desativado não pode ser reativado");
    }
    this.status = UserStatus.ACTIVE;
  }

  public void deactivate(String reason) {
    this.status = UserStatus.DEACTIVATED;
    this.deactivatedAt = Instant.now();
    this.deactivatedReason = reason;
  }

  public boolean canLogin() {
    return status == UserStatus.ACTIVE;
  }

  public void linkPerson(UUID personId) {
    this.personId = personId;
  }

  // ------------------------------------------------------------------
  // JPA hooks
  // ------------------------------------------------------------------

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserAccount other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
