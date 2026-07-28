package dev.dimi.erp.identidade.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de {@link PasswordHistoryEntry}: {@code (user_id, created_at)}. */
@Embeddable
public class PasswordHistoryId implements Serializable {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected PasswordHistoryId() {
    // jpa
  }

  public PasswordHistoryId(UUID userId, Instant createdAt) {
    this.userId = Objects.requireNonNull(userId);
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PasswordHistoryId other)) return false;
    return Objects.equals(userId, other.userId) && Objects.equals(createdAt, other.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, createdAt);
  }
}
