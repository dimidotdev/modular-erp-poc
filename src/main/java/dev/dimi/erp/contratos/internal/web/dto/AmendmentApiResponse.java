package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.AmendmentType;
import dev.dimi.erp.contratos.internal.domain.Amendment;
import dev.dimi.erp.contratos.internal.domain.Amendment.AmendmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representação REST de {@code contract_amendment}. */
public record AmendmentApiResponse(
    UUID id,
    UUID contractId,
    AmendmentType type,
    LocalDate effectiveFrom,
    String description,
    LocalDate newEndDate,
    BigDecimal newBaseValue,
    String payload,
    AmendmentStatus status,
    Instant createdAt,
    Instant effectiveAt,
    Instant cancelledAt,
    UUID createdBy,
    Instant updatedAt) {

  public static AmendmentApiResponse from(Amendment a) {
    return new AmendmentApiResponse(
        a.getId(),
        a.getContractId(),
        a.getType(),
        a.getEffectiveFrom(),
        a.getDescription(),
        a.getNewEndDate(),
        a.getNewBaseValue(),
        a.getPayload(),
        a.getStatus(),
        a.getCreatedAt(),
        a.getEffectiveAt(),
        a.getCancelledAt(),
        a.getCreatedBy(),
        a.getUpdatedAt());
  }
}
