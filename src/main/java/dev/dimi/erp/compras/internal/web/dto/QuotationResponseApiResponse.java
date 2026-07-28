package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.internal.application.QuotationResponseSummary;
import dev.dimi.erp.compras.internal.domain.QuotationResponseId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;

/** Response de linha de resposta de cotação. */
public record QuotationResponseApiResponse(
    QuotationResponseId id,
    QuotationId quotationId,
    PersonId supplierId,
    int requestItemLine,
    PackagingId packagingId,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    String notes) {

  public static QuotationResponseApiResponse from(QuotationResponseSummary s) {
    return new QuotationResponseApiResponse(
        s.id(),
        s.quotationId(),
        s.supplierId(),
        s.requestItemLine(),
        s.packagingId(),
        s.description(),
        s.quantity(),
        s.unitPrice(),
        s.notes());
  }
}
