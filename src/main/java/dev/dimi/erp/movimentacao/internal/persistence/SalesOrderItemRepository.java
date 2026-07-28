package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.internal.domain.SalesOrderItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderItemRepository extends JpaRepository<SalesOrderItem, UUID> {

  List<SalesOrderItem> findByOrderIdOrderByLineNumberAsc(UUID orderId);

  void deleteByOrderId(UUID orderId);
}
