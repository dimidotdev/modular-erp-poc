package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.FinancialRecordStatus;
import dev.dimi.erp.financeiro.internal.domain.FinancialRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Snapshot completo de MVF com parcelas e rateios. */
public record FinancialRecordSummary(
    FinancialRecordId id,
    long version,
    long number,
    BranchId branchId,
    FinancialDirection direction,
    PersonId counterpartyId,
    String documentNumber,
    Instant issuedAt,
    BigDecimal totalValue,
    FinancialRecordStatus status,
    FinancialOriginType originType,
    UUID originId,
    String notes,
    String idempotencyKey,
    Instant createdAt,
    Instant updatedAt,
    List<FinancialInstallmentSummary> installments,
    List<FinancialApportionmentSummary> apportionments) {

  public static FinancialRecordSummary of(
      FinancialRecord r,
      List<FinancialInstallmentSummary> installments,
      List<FinancialApportionmentSummary> apportionments) {
    return new FinancialRecordSummary(
        r.id(),
        r.getVersion(),
        r.getNumber(),
        r.branchIdTyped(),
        r.getDirection(),
        r.counterpartyIdTyped(),
        r.getDocumentNumber(),
        r.getIssuedAt(),
        r.getTotalValue(),
        r.getStatus(),
        r.getOriginType(),
        r.getOriginId(),
        r.getNotes(),
        r.getIdempotencyKey(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        installments,
        apportionments);
  }
}
