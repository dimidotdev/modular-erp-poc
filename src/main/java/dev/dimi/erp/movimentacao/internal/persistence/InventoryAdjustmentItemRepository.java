package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustmentItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryAdjustmentItemRepository
    extends JpaRepository<InventoryAdjustmentItem, UUID> {

  List<InventoryAdjustmentItem> findByAdjustmentId(UUID adjustmentId);

  void deleteByAdjustmentId(UUID adjustmentId);
}
