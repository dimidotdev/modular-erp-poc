package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.Employee;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

  @Query(
      """
      SELECT e FROM Employee e
      WHERE e.personId = :personId
        AND e.branchId = :branchId
        AND e.terminatedAt IS NULL
      """)
  Optional<Employee> findActiveByPersonAndBranch(
      @Param("personId") UUID personId, @Param("branchId") UUID branchId);

  List<Employee> findByPersonId(UUID personId);

  List<Employee> findByBranchIdAndActiveTrue(UUID branchId);
}
