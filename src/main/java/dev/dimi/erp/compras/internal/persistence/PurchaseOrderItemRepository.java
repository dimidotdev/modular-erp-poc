package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.PurchaseOrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, UUID> {

  List<PurchaseOrderItem> findByOrderIdOrderByLineNumberAsc(UUID orderId);

  void deleteByOrderId(UUID orderId);
}
