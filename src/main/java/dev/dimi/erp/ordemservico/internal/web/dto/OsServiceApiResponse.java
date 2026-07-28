package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.internal.domain.OsService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representação REST de {@code service_order_service}. */
public record OsServiceApiResponse(
    UUID id,
    UUID osId,
    int lineNumber,
    String serviceCode,
    String description,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal discountValue,
    BigDecimal netValue,
    BigDecimal issqnRate,
    BigDecimal issqnValue,
    String pisCofinsCalc,
    Instant createdAt,
    Instant updatedAt) {

  public static OsServiceApiResponse from(OsService s) {
    return new OsServiceApiResponse(
        s.getId(),
        s.getOsId(),
        s.getLineNumber(),
        s.getServiceCode(),
        s.getDescription(),
        s.getQuantity(),
        s.getUnitPrice(),
        s.getDiscountValue(),
        s.getNetValue(),
        s.getIssqnRate(),
        s.getIssqnValue(),
        s.getPisCofinsCalc(),
        s.getCreatedAt(),
        s.getUpdatedAt());
  }
}
