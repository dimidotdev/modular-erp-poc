package dev.dimi.erp.identidade.internal.domain;

import dev.dimi.erp.identidade.RoleId;
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
 * Join entre {@link Role} e o catálogo global {@code shared.permission}.
 *
 * <p>{@code permission_code} é referência soft (sem FK formal) ao {@code shared.permission.code}.
 * Validação de existência fica em service (cross-schema FK complica Flyway por tenant).
 */
@Entity
@Table(name = "role_permission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id"})
public class RolePermissionAssignment {

  @EmbeddedId private RolePermissionId id;

  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  public static RolePermissionAssignment grant(RoleId roleId, String permissionCode) {
    RolePermissionAssignment a = new RolePermissionAssignment();
    a.id = new RolePermissionId(roleId.value(), permissionCode);
    return a;
  }

  @PrePersist
  void onPersist() {
    if (grantedAt == null) {
      grantedAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RolePermissionAssignment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
