package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.PurchaseReceipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, UUID> {

  Optional<PurchaseReceipt> findByOrderIdAndNumber(UUID orderId, long number);

  Optional<PurchaseReceipt> findByIdempotencyKey(String idempotencyKey);

  List<PurchaseReceipt> findByOrderId(UUID orderId);

  Optional<PurchaseReceipt> findByDfeDocumentId(UUID dfeDocumentId);
}
