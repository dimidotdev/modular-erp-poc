package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.internal.domain.StockLocation;
import dev.dimi.erp.estoque.internal.persistence.StockLocationRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de {@link StockLocation}. Default-único enforced via {@code clearDefaultForBranch} antes de
 * persistir o novo registro (mesma armadilha de partial unique index do produto).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManageStockLocationService {

  private final StockLocationRepository locations;

  @Transactional
  public StockLocationSummary register(
      BranchId branchId, String code, String name, boolean asDefault) {
    if (locations.existsByBranchIdAndCode(branchId.value(), code)) {
      throw new InvalidStockOperationException(
          "duplicate-location-code", "Já existe local com este code nesta filial.");
    }
    if (asDefault) {
      locations.clearDefaultForBranch(branchId.value());
    }
    StockLocation saved =
        locations.save(StockLocation.create(branchId.value(), code, name, asDefault));
    log.info(
        "stock-location created id={} branch={} code={} default={}",
        saved.id().value(),
        branchId.value(),
        code,
        asDefault);
    return StockLocationSummary.of(saved);
  }

  @Transactional
  public StockLocationSummary rename(StockLocationId locationId, String name) {
    StockLocation l = load(locationId);
    l.rename(name);
    return StockLocationSummary.of(l);
  }

  @Transactional
  public StockLocationSummary markDefault(StockLocationId locationId) {
    StockLocation l = load(locationId);
    if (!l.isDefault()) {
      locations.clearDefaultForBranch(l.getBranchId());
      l.markDefault();
    }
    return StockLocationSummary.of(l);
  }

  @Transactional
  public StockLocationSummary deactivate(StockLocationId locationId) {
    StockLocation l = load(locationId);
    l.deactivate();
    return StockLocationSummary.of(l);
  }

  @Transactional
  public StockLocationSummary reactivate(StockLocationId locationId) {
    StockLocation l = load(locationId);
    l.reactivate();
    return StockLocationSummary.of(l);
  }

  @Transactional(readOnly = true)
  public StockLocationSummary findById(StockLocationId locationId) {
    return StockLocationSummary.of(load(locationId));
  }

  @Transactional(readOnly = true)
  public List<StockLocationSummary> listByBranch(BranchId branchId) {
    return locations.findByBranchId(branchId.value()).stream()
        .map(StockLocationSummary::of)
        .toList();
  }

  private StockLocation load(StockLocationId locationId) {
    return locations
        .findById(locationId.value())
        .orElseThrow(() -> new StockLocationNotFoundException(locationId));
  }
}
