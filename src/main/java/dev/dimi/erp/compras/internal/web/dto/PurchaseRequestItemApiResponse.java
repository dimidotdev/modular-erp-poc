package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.internal.application.PurchaseRequestItemSummary;
import dev.dimi.erp.compras.internal.domain.PurchaseRequestItemId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de linha de SC. */
public record PurchaseRequestItemApiResponse(
    PurchaseRequestItemId id,
    PurchaseRequestId requestId,
    int lineNumber,
    PackagingId packagingId,
    String description,
    BigDecimal quantity,
    BigDecimal estimatedUnitCost,
    String notes) {

  public static PurchaseRequestItemApiResponse from(PurchaseRequestItemSummary s) {
    return new PurchaseRequestItemApiResponse(
        s.id(),
        s.requestId(),
        s.lineNumber(),
        s.packagingId(),
        s.description(),
        s.quantity(),
        s.estimatedUnitCost(),
        s.notes());
  }
}
