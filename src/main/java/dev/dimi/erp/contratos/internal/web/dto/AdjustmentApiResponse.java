package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.AdjustmentIndex;
import dev.dimi.erp.contratos.internal.domain.Adjustment;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Representação REST de {@code contract_adjustment} (reajuste append-only). */
public record AdjustmentApiResponse(
    UUID id,
    UUID contractId,
    LocalDate appliedOn,
    AdjustmentIndex index,
    BigDecimal percentage,
    BigDecimal oldBaseValue,
    BigDecimal newBaseValue,
    String notes,
    UUID appliedBy,
    Instant createdAt) {

  public static AdjustmentApiResponse from(Adjustment a) {
    return new AdjustmentApiResponse(
        a.getId(),
        a.getContractId(),
        a.getAppliedOn(),
        a.getIndexKind(),
        a.getPercentage(),
        a.getOldBaseValue(),
        a.getNewBaseValue(),
        a.getNotes(),
        a.getAppliedBy(),
        a.getCreatedAt());
  }
}
