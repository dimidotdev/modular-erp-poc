package dev.dimi.erp.identidade.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de {@link UserRoleAssignment}: {@code (user_id, role_id)}. */
@Embeddable
public class UserRoleId implements Serializable {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  protected UserRoleId() {
    // jpa
  }

  public UserRoleId(UUID userId, UUID roleId) {
    this.userId = Objects.requireNonNull(userId, "userId");
    this.roleId = Objects.requireNonNull(roleId, "roleId");
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserRoleId other)) return false;
    return Objects.equals(userId, other.userId) && Objects.equals(roleId, other.roleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, roleId);
  }
}
