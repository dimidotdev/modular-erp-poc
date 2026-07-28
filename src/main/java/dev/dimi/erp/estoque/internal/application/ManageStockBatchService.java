package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.internal.domain.StockBatch;
import dev.dimi.erp.estoque.internal.persistence.StockBatchRepository;
import dev.dimi.erp.produto.PackagingId;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD de {@link StockBatch}. Valida unicidade do code dentro da embalagem. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManageStockBatchService {

  private final StockBatchRepository batches;

  @Transactional
  public StockBatchSummary register(
      PackagingId packagingId,
      String code,
      LocalDate manufacturedOn,
      LocalDate expiresOn,
      String notes) {
    if (batches.existsByPackagingIdAndCode(packagingId.value(), code)) {
      throw new InvalidStockOperationException(
          "duplicate-batch-code", "Já existe lote com este code nesta embalagem.");
    }
    StockBatch saved =
        batches.save(
            StockBatch.create(packagingId.value(), code, manufacturedOn, expiresOn, notes));
    log.info(
        "stock-batch created id={} packaging={} code={} expires={}",
        saved.id().value(),
        packagingId.value(),
        code,
        expiresOn);
    return StockBatchSummary.of(saved);
  }

  @Transactional
  public StockBatchSummary update(
      StockBatchId batchId, LocalDate manufacturedOn, LocalDate expiresOn, String notes) {
    StockBatch b = load(batchId);
    b.updateMetadata(manufacturedOn, expiresOn, notes);
    return StockBatchSummary.of(b);
  }

  @Transactional(readOnly = true)
  public StockBatchSummary findById(StockBatchId batchId) {
    return StockBatchSummary.of(load(batchId));
  }

  @Transactional(readOnly = true)
  public List<StockBatchSummary> listByPackaging(PackagingId packagingId) {
    return batches.findByPackagingId(packagingId.value()).stream()
        .map(StockBatchSummary::of)
        .toList();
  }

  private StockBatch load(StockBatchId batchId) {
    return batches
        .findById(batchId.value())
        .orElseThrow(() -> new StockBatchNotFoundException(batchId));
  }
}
