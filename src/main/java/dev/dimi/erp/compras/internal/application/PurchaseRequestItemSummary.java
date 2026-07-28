package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.internal.domain.PurchaseRequestItem;
import dev.dimi.erp.compras.internal.domain.PurchaseRequestItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Snapshot de linha de SC. */
public record PurchaseRequestItemSummary(
    PurchaseRequestItemId id,
    PurchaseRequestId requestId,
    int lineNumber,
    PackagingId packagingId,
    String description,
    BigDecimal quantity,
    BigDecimal estimatedUnitCost,
    String notes) {

  public static PurchaseRequestItemSummary of(PurchaseRequestItem i) {
    return new PurchaseRequestItemSummary(
        i.id(),
        i.requestIdTyped(),
        i.getLineNumber(),
        i.packagingIdTyped(),
        i.getDescription(),
        i.getQuantity(),
        i.getEstimatedUnitCost(),
        i.getNotes());
  }
}
