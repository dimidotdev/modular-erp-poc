package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.internal.domain.Sale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, UUID> {

  Optional<Sale> findByBranchIdAndNumber(UUID branchId, long number);

  Optional<Sale> findByIdempotencyKey(String idempotencyKey);

  Page<Sale> findByBranchIdAndStatus(UUID branchId, SaleStatus status, Pageable pageable);

  List<Sale> findByCustomerId(UUID customerId);

  /**
   * Vendas geradas a partir de um pedido específico (1:1 esperado, mas modelado 1:N por segurança).
   */
  List<Sale> findBySalesOrderId(UUID salesOrderId);
}
