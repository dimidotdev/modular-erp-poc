package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.PriceTable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceTableRepository extends JpaRepository<PriceTable, UUID> {

  List<PriceTable> findByBranchId(UUID branchId);

  /**
   * Tabelas ativas vigentes em {@code date} para uma filial específica OU globais (branchId NULL).
   * Ordenadas com tabela de filial primeiro (mais específica vence em {@code PriceLookupPort} slice
   * 4.2).
   */
  @Query(
      """
      SELECT t FROM PriceTable t
      WHERE t.active = true
        AND t.validFrom <= :date
        AND (t.validUntil IS NULL OR t.validUntil >= :date)
        AND (t.branchId = :branchId OR t.branchId IS NULL)
      ORDER BY CASE WHEN t.branchId IS NULL THEN 1 ELSE 0 END, t.validFrom DESC
      """)
  List<PriceTable> findActiveOn(@Param("branchId") UUID branchId, @Param("date") LocalDate date);
}
