package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.OsBillingType;
import dev.dimi.erp.ordemservico.internal.domain.Billing;
import dev.dimi.erp.ordemservico.internal.domain.BillingType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representação REST de {@code service_order_billing}. O mapping do enum interno {@link
 * BillingType} para o público {@link OsBillingType} acontece em {@link #from(Billing)} — domínio
 * interno fica encapsulado, API pública usa o enum oficial.
 */
public record BillingApiResponse(
    UUID id,
    UUID osId,
    OsBillingType billingType,
    Instant issuedAt,
    UUID nfeInvoiceId,
    UUID nfseInvoiceId,
    BigDecimal productsAmount,
    BigDecimal servicesAmount,
    BigDecimal total,
    String notes,
    UUID issuedBy,
    Instant cancelledAt,
    String cancellationReason) {

  public static BillingApiResponse from(Billing b) {
    return new BillingApiResponse(
        b.getId(),
        b.getOsId(),
        toPublic(b.getBillingType()),
        b.getIssuedAt(),
        b.getNfeInvoiceId(),
        b.getNfseInvoiceId(),
        b.getProductsAmount(),
        b.getServicesAmount(),
        b.getTotal(),
        b.getNotes(),
        b.getIssuedBy(),
        b.getCancelledAt(),
        b.getCancellationReason());
  }

  private static OsBillingType toPublic(BillingType internal) {
    if (internal == null) return null;
    return switch (internal) {
      case PARTIAL -> OsBillingType.PARTIAL;
      case FINAL -> OsBillingType.FINAL;
    };
  }
}
