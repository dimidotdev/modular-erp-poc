package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.PriceTableItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceTableItemRepository extends JpaRepository<PriceTableItem, UUID> {

  List<PriceTableItem> findByPriceTableId(UUID priceTableId);

  List<PriceTableItem> findByPackagingId(UUID packagingId);

  Optional<PriceTableItem> findByPriceTableIdAndPackagingId(UUID priceTableId, UUID packagingId);

  void deleteByPriceTableId(UUID priceTableId);
}
