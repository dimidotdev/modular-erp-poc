package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

  Optional<SalesOrder> findByBranchIdAndNumber(UUID branchId, long number);

  Page<SalesOrder> findByBranchIdAndStatus(
      UUID branchId, SalesOrderStatus status, Pageable pageable);

  List<SalesOrder> findByCustomerId(UUID customerId);
}
