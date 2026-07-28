package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.SupplierReturnItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierReturnItemRepository extends JpaRepository<SupplierReturnItem, UUID> {

  List<SupplierReturnItem> findBySupplierReturnId(UUID supplierReturnId);

  List<SupplierReturnItem> findByReceiptItemId(UUID receiptItemId);
}
