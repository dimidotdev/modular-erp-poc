package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {

  Optional<ServiceOrder> findByBranchIdAndNumber(UUID branchId, long number);

  Page<ServiceOrder> findByBranchId(UUID branchId, Pageable pageable);

  Page<ServiceOrder> findByBranchIdAndStatusId(UUID branchId, UUID statusId, Pageable pageable);

  Page<ServiceOrder> findByCustomerId(UUID customerId, Pageable pageable);

  @Query("select coalesce(max(s.number), 0) from ServiceOrder s where s.branchId = :branchId")
  long maxNumberByBranch(UUID branchId);
}
