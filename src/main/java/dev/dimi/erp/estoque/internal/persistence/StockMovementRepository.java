package dev.dimi.erp.estoque.internal.persistence;

import dev.dimi.erp.estoque.internal.domain.StockMovement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

  /** RN-EST-005: idempotência por chave natural. Slice 4.4 usa para detectar duplicatas. */
  Optional<StockMovement> findByIdempotencyKey(String idempotencyKey);

  /** Rastreio cross-module: "todos movimentos vindos da venda X / compra Y / OS Z". */
  List<StockMovement> findByOriginTypeAndOriginId(String originType, UUID originId);

  /**
   * Ledger paginado por (packaging, branch) em ordem cronológica reversa — endpoint público de
   * estoque entrega isso no slice 4.4 (p95 < 30ms).
   */
  @Query(
      "select m from StockMovement m "
          + "where m.packagingId = :packagingId "
          + "  and m.branchId = :branchId "
          + "  and m.occurredAt between :from and :to "
          + "order by m.occurredAt desc")
  Page<StockMovement> pageLedger(
      @Param("packagingId") UUID packagingId,
      @Param("branchId") UUID branchId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);
}
