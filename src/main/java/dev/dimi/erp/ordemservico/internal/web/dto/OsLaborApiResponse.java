package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.internal.domain.OsLabor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Representação REST de {@code service_order_labor}. */
public record OsLaborApiResponse(
    UUID id,
    UUID osId,
    UUID employeeId,
    Instant startedAt,
    Instant finishedAt,
    BigDecimal hours,
    String description,
    BigDecimal internalCost,
    boolean isBillable,
    Integer relatedServiceLine,
    Instant createdAt) {

  public static OsLaborApiResponse from(OsLabor l) {
    return new OsLaborApiResponse(
        l.getId(),
        l.getOsId(),
        l.getEmployeeId(),
        l.getStartedAt(),
        l.getFinishedAt(),
        l.getHours(),
        l.getDescription(),
        l.getInternalCost(),
        l.isBillable(),
        l.getRelatedServiceLine(),
        l.getCreatedAt());
  }
}
