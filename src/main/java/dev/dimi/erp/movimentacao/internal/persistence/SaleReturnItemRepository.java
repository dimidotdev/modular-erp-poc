package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.internal.domain.SaleReturnItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, UUID> {

  List<SaleReturnItem> findByReturnId(UUID returnId);

  List<SaleReturnItem> findBySaleItemId(UUID saleItemId);

  void deleteByReturnId(UUID returnId);
}
