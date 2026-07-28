package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.internal.domain.SaleItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

  List<SaleItem> findBySaleIdOrderByLineNumberAsc(UUID saleId);

  void deleteBySaleId(UUID saleId);
}
