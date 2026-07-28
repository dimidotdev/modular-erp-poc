package dev.dimi.erp.identidade.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de {@link RolePermissionAssignment}: {@code (role_id, permission_code)}. */
@Embeddable
public class RolePermissionId implements Serializable {

  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Column(name = "permission_code", nullable = false)
  private String permissionCode;

  protected RolePermissionId() {
    // jpa
  }

  public RolePermissionId(UUID roleId, String permissionCode) {
    this.roleId = Objects.requireNonNull(roleId, "roleId");
    this.permissionCode = Objects.requireNonNull(permissionCode, "permissionCode");
  }

  public UUID getRoleId() {
    return roleId;
  }

  public String getPermissionCode() {
    return permissionCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RolePermissionId other)) return false;
    return Objects.equals(roleId, other.roleId)
        && Objects.equals(permissionCode, other.permissionCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roleId, permissionCode);
  }
}
