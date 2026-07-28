package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationSupplierStatus;
import dev.dimi.erp.compras.internal.domain.QuotationSupplier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Snapshot de {@code quotation_supplier}. */
public record QuotationSupplierSummary(
    QuotationId quotationId,
    PersonId supplierId,
    QuotationSupplierStatus status,
    Instant invitedAt,
    Instant respondedAt,
    UUID paymentConditionId,
    Integer deliveryDays,
    BigDecimal freightValue,
    String notes) {

  public static QuotationSupplierSummary of(QuotationSupplier s) {
    return new QuotationSupplierSummary(
        s.quotationIdTyped(),
        s.supplierIdTyped(),
        s.getStatus(),
        s.getInvitedAt(),
        s.getRespondedAt(),
        s.getPaymentConditionId(),
        s.getDeliveryDays(),
        s.getFreightValue(),
        s.getNotes());
  }
}
