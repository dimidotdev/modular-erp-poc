package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseReceiptItemRepository extends JpaRepository<PurchaseReceiptItem, UUID> {

  List<PurchaseReceiptItem> findByReceiptId(UUID receiptId);

  List<PurchaseReceiptItem> findByOrderItemId(UUID orderItemId);
}
