package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link InventoryAdjustmentId} não encontrou ajuste. */
public class InventoryAdjustmentNotFoundException extends DomainException {

  private final InventoryAdjustmentId adjustmentId;

  public InventoryAdjustmentNotFoundException(InventoryAdjustmentId adjustmentId) {
    super(
        "movimentacao.adjustment-not-found",
        HttpStatus.NOT_FOUND,
        "Ajuste de inventário não encontrado",
        "Não existe ajuste com este id neste tenant.");
    this.adjustmentId = adjustmentId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("adjustmentId", adjustmentId.value().toString());
  }
}
