package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationSupplierStatus;
import dev.dimi.erp.compras.internal.application.QuotationSupplierSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response de fornecedor convidado em cotação. */
public record QuotationSupplierApiResponse(
    QuotationId quotationId,
    PersonId supplierId,
    QuotationSupplierStatus status,
    Instant invitedAt,
    Instant respondedAt,
    UUID paymentConditionId,
    Integer deliveryDays,
    BigDecimal freightValue,
    String notes) {

  public static QuotationSupplierApiResponse from(QuotationSupplierSummary s) {
    return new QuotationSupplierApiResponse(
        s.quotationId(),
        s.supplierId(),
        s.status(),
        s.invitedAt(),
        s.respondedAt(),
        s.paymentConditionId(),
        s.deliveryDays(),
        s.freightValue(),
        s.notes());
  }
}
