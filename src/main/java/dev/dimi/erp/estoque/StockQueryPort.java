package dev.dimi.erp.estoque;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Consultas read-only de estoque expostas para outros módulos (movimentação consulta antes de
 * fechar venda, fiscal puxa custo para NFe, dashboards leem saldo agregado).
 */
public interface StockQueryPort {

  /** Snapshot agregado de todas as tuplas de uma embalagem numa filial. */
  List<StockSnapshotView> snapshotsFor(PackagingId packagingId, BranchId branchId);

  /**
   * Saldo total disponível (soma de {@code on_hand - reserved} em todos os locais e lotes) — útil
   * para validar pedido antes da reserva concreta. {@code empty} quando não há tuplas ativas.
   */
  Optional<BigDecimal> totalAvailable(PackagingId packagingId, BranchId branchId);

  /**
   * Local default ativo da filial (RN-EST-013). Usado por movimentação quando o caller não
   * especifica location explícito no fechamento da venda ou em saídas avulsas.
   */
  Optional<StockLocationId> findDefaultLocation(BranchId branchId);

  record StockSnapshotView(
      StockSnapshotId id,
      PackagingId packagingId,
      BranchId branchId,
      StockLocationId locationId,
      StockBatchId batchId,
      BigDecimal quantityOnHand,
      BigDecimal quantityReserved,
      BigDecimal quantityAvailable,
      BigDecimal weightedAvgCost) {}
}
