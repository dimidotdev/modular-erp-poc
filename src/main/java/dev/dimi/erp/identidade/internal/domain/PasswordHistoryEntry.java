package dev.dimi.erp.identidade.internal.domain;

import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Histórico de senhas (hash Argon2id) de um usuário. Política: rejeitar reuso das últimas 5.
 *
 * <p>Apenas o hash é armazenado — nunca senha em claro. Job futuro de limpeza mantém só as últimas
 * N entradas por usuário.
 */
@Entity
@Table(name = "password_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id"})
public class PasswordHistoryEntry {

  @EmbeddedId private PasswordHistoryId id;

  @Column(name = "password_hash", nullable = false, updatable = false)
  private String passwordHash;

  public static PasswordHistoryEntry of(UserId userId, String passwordHash) {
    PasswordHistoryEntry e = new PasswordHistoryEntry();
    e.id = new PasswordHistoryId(userId.value(), Instant.now());
    e.passwordHash = Objects.requireNonNull(passwordHash);
    return e;
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      throw new IllegalStateException(
          "PasswordHistoryEntry persistido sem id — use PasswordHistoryEntry.of(userId, hash)");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PasswordHistoryEntry other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
