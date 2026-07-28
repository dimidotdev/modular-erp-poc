package dev.dimi.erp.estoque.internal.persistence;

import dev.dimi.erp.estoque.internal.domain.StockBatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

  List<StockBatch> findByPackagingId(UUID packagingId);

  Optional<StockBatch> findByPackagingIdAndCode(UUID packagingId, String code);

  boolean existsByPackagingIdAndCode(UUID packagingId, String code);

  /** Lotes que vencem antes de {@code threshold} — útil para alertas pré-validade. */
  @Query(
      "select b from StockBatch b where b.packagingId = :packagingId "
          + "and b.expiresOn is not null and b.expiresOn <= :threshold "
          + "order by b.expiresOn asc")
  List<StockBatch> findExpiringBefore(
      @Param("packagingId") UUID packagingId, @Param("threshold") LocalDate threshold);
}
