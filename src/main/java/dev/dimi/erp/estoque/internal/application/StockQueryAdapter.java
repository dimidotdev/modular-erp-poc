package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.estoque.internal.persistence.StockLocationRepository;
import dev.dimi.erp.estoque.internal.persistence.StockSnapshotRepository;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação do {@link StockQueryPort}. Consultas read-only sem lock — outros módulos podem
 * chamar de qualquer transação. Para mutação, usar {@link StockInventoryService} via {@code
 * StockInventoryPort}.
 */
@Component
@RequiredArgsConstructor
class StockQueryAdapter implements StockQueryPort {

  private final StockSnapshotRepository snapshots;
  private final StockLocationRepository locations;

  @Override
  @Transactional(readOnly = true)
  public List<StockSnapshotView> snapshotsFor(PackagingId packagingId, BranchId branchId) {
    return snapshots.findByPackagingIdAndBranchId(packagingId.value(), branchId.value()).stream()
        .map(
            s ->
                new StockSnapshotView(
                    s.id(),
                    s.packagingIdTyped(),
                    s.branchIdTyped(),
                    s.locationIdTyped(),
                    s.batchIdTyped(),
                    s.getQuantityOnHand(),
                    s.getQuantityReserved(),
                    s.quantityAvailable(),
                    s.getWeightedAvgCost()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<BigDecimal> totalAvailable(PackagingId packagingId, BranchId branchId) {
    List<StockSnapshotView> views = snapshotsFor(packagingId, branchId);
    if (views.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        views.stream()
            .map(StockSnapshotView::quantityAvailable)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<StockLocationId> findDefaultLocation(BranchId branchId) {
    return locations
        .findByBranchIdAndIsDefaultTrue(branchId.value())
        .filter(l -> l.isActive())
        .map(l -> l.id());
  }
}
