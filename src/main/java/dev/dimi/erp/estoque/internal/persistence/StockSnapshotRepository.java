package dev.dimi.erp.estoque.internal.persistence;

import dev.dimi.erp.estoque.internal.domain.StockSnapshot;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockSnapshotRepository extends JpaRepository<StockSnapshot, UUID> {

  List<StockSnapshot> findByPackagingId(UUID packagingId);

  List<StockSnapshot> findByPackagingIdAndBranchId(UUID packagingId, UUID branchId);

  /**
   * Lookup pela tupla natural (packaging, branch, location, batch). {@code batchId} null vira "sem
   * lote" — JPQL não casa NULL com igualdade direta, então o branch null é tratado à parte.
   */
  @Query(
      "select s from StockSnapshot s "
          + "where s.packagingId = :packagingId "
          + "  and s.branchId = :branchId "
          + "  and s.locationId = :locationId "
          + "  and ((:batchId is null and s.batchId is null) or s.batchId = :batchId)")
  Optional<StockSnapshot> findByKey(
      @Param("packagingId") UUID packagingId,
      @Param("branchId") UUID branchId,
      @Param("locationId") UUID locationId,
      @Param("batchId") UUID batchId);

  /**
   * RN-EST-004: lock pessimista antes de qualquer recálculo. Garante que duas transações
   * concorrentes não recalculem WAC em cima do mesmo snapshot. Slice 4.4 vai usar este lookup
   * dentro de toda operação que mute o snapshot.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from StockSnapshot s "
          + "where s.packagingId = :packagingId "
          + "  and s.branchId = :branchId "
          + "  and s.locationId = :locationId "
          + "  and ((:batchId is null and s.batchId is null) or s.batchId = :batchId)")
  Optional<StockSnapshot> findByKeyForUpdate(
      @Param("packagingId") UUID packagingId,
      @Param("branchId") UUID branchId,
      @Param("locationId") UUID locationId,
      @Param("batchId") UUID batchId);
}
