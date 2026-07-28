package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

  Optional<InventoryAdjustment> findByBranchIdAndNumber(UUID branchId, long number);

  Page<InventoryAdjustment> findByBranchId(UUID branchId, Pageable pageable);
}
