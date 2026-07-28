package dev.dimi.erp.identidade.internal.domain;

import dev.dimi.erp.identidade.RoleId;
import dev.dimi.erp.identidade.UserId;
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

/** Atribuição de role a um usuário. */
@Entity
@Table(name = "user_role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id"})
public class UserRoleAssignment {

  @EmbeddedId private UserRoleId id;

  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  @Column(name = "assigned_by")
  private UUID assignedBy;

  /**
   * Cria uma atribuição. {@code assignedBy} pode ser {@code null} no bootstrap inicial (sem usuário
   * autenticado ainda).
   */
  public static UserRoleAssignment assign(UserId userId, RoleId roleId, UserId assignedBy) {
    UserRoleAssignment a = new UserRoleAssignment();
    a.id = new UserRoleId(userId.value(), roleId.value());
    a.assignedBy = assignedBy != null ? assignedBy.value() : null;
    return a;
  }

  @PrePersist
  void onPersist() {
    if (assignedAt == null) {
      assignedAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UserRoleAssignment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
