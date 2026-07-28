package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.domain.QuotationResponse;
import dev.dimi.erp.compras.internal.domain.QuotationResponseId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Snapshot de linha de resposta de cotação. */
public record QuotationResponseSummary(
    QuotationResponseId id,
    QuotationId quotationId,
    PersonId supplierId,
    int requestItemLine,
    PackagingId packagingId,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    String notes) {

  public static QuotationResponseSummary of(QuotationResponse r) {
    return new QuotationResponseSummary(
        r.id(),
        r.quotationIdTyped(),
        r.supplierIdTyped(),
        r.getRequestItemLine(),
        r.packagingIdTyped(),
        r.getDescription(),
        r.getQuantity(),
        r.getUnitPrice(),
        r.getNotes());
  }
}
