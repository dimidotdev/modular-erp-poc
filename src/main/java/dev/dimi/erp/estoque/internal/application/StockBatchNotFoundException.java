package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link StockBatchId} não encontrou lote. */
public class StockBatchNotFoundException extends DomainException {

  private final StockBatchId batchId;

  public StockBatchNotFoundException(StockBatchId batchId) {
    super(
        "estoque.batch-not-found",
        HttpStatus.NOT_FOUND,
        "Lote não encontrado",
        "Não existe lote com este id neste tenant.");
    this.batchId = batchId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("batchId", batchId.value().toString());
  }
}
