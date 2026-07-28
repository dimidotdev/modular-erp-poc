package dev.dimi.erp.identidade.internal.persistence;

import dev.dimi.erp.identidade.internal.domain.UserRoleAssignment;
import dev.dimi.erp.identidade.internal.domain.UserRoleId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleAssignmentRepository
    extends JpaRepository<UserRoleAssignment, UserRoleId> {

  @Query("SELECT a.id.roleId FROM UserRoleAssignment a WHERE a.id.userId = :userId")
  List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);

  @Query("SELECT a FROM UserRoleAssignment a WHERE a.id.userId = :userId")
  List<UserRoleAssignment> findByUserId(@Param("userId") UUID userId);

  long countByIdRoleId(UUID roleId);

  @Query(
      value =
          """
          SELECT count(*)
          FROM user_account u
          JOIN user_role ur ON ur.user_id = u.id
          JOIN role r ON r.id = ur.role_id
          WHERE u.status = 'ACTIVE' AND r.code = :roleCode
          """,
      nativeQuery = true)
  long countActiveUsersByRoleCode(@Param("roleCode") String roleCode);
}
