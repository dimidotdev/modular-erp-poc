package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.compras.internal.domain.PurchaseRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, UUID> {

  Optional<PurchaseRequest> findByBranchIdAndNumber(UUID branchId, long number);

  Page<PurchaseRequest> findByBranchIdAndStatus(
      UUID branchId, PurchaseRequestStatus status, Pageable pageable);
}
