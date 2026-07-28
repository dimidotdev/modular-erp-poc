package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.internal.domain.StockTransferItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, UUID> {

  List<StockTransferItem> findByTransferIdOrderByLineNumberAsc(UUID transferId);

  void deleteByTransferId(UUID transferId);
}
