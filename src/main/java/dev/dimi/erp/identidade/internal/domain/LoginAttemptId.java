package dev.dimi.erp.identidade.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Chave composta de {@link LoginAttempt}: {@code (id, attempted_at)}.
 *
 * <p>{@code attemptedAt} entra na PK porque a tabela é particionada por dia em {@code attempted_at}
 * (Postgres exige que a coluna de partição faça parte da PK).
 */
@Embeddable
public class LoginAttemptId implements Serializable {

  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "attempted_at", nullable = false, updatable = false)
  private Instant attemptedAt;

  protected LoginAttemptId() {
    // jpa
  }

  public LoginAttemptId(UUID id, Instant attemptedAt) {
    this.id = Objects.requireNonNull(id);
    this.attemptedAt = Objects.requireNonNull(attemptedAt);
  }

  public UUID getId() {
    return id;
  }

  public Instant getAttemptedAt() {
    return attemptedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LoginAttemptId other)) return false;
    return Objects.equals(id, other.id) && Objects.equals(attemptedAt, other.attemptedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, attemptedAt);
  }
}
