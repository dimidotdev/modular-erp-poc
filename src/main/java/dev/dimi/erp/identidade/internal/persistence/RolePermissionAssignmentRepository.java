package dev.dimi.erp.identidade.internal.persistence;

import dev.dimi.erp.identidade.internal.domain.RolePermissionAssignment;
import dev.dimi.erp.identidade.internal.domain.RolePermissionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionAssignmentRepository
    extends JpaRepository<RolePermissionAssignment, RolePermissionId> {

  @Query("SELECT a.id.permissionCode FROM RolePermissionAssignment a WHERE a.id.roleId = :roleId")
  List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId);

  /** Resolve as permissions de todas as roles do usuário (união). */
  @Query(
      """
      SELECT DISTINCT rp.id.permissionCode
      FROM RolePermissionAssignment rp
      WHERE rp.id.roleId IN (
        SELECT ur.id.roleId FROM UserRoleAssignment ur WHERE ur.id.userId = :userId
      )
      """)
  List<String> findPermissionCodesByUserId(@Param("userId") UUID userId);
}
