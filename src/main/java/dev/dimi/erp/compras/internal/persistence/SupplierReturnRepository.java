package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.SupplierReturn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, UUID> {

  List<SupplierReturn> findByReceiptId(UUID receiptId);
}
