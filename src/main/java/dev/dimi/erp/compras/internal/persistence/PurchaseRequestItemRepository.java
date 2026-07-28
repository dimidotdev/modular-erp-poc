package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.PurchaseRequestItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRequestItemRepository extends JpaRepository<PurchaseRequestItem, UUID> {

  List<PurchaseRequestItem> findByRequestIdOrderByLineNumberAsc(UUID requestId);

  void deleteByRequestId(UUID requestId);
}
