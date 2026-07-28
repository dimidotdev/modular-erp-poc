package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.PurchaseOrderStatus;
import dev.dimi.erp.compras.internal.domain.PurchaseOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

  Optional<PurchaseOrder> findByBranchIdAndNumber(UUID branchId, long number);

  Page<PurchaseOrder> findByBranchIdAndStatus(
      UUID branchId, PurchaseOrderStatus status, Pageable pageable);

  List<PurchaseOrder> findBySupplierId(UUID supplierId);

  List<PurchaseOrder> findByQuotationId(UUID quotationId);
}
