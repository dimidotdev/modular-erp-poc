package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.FinancialRecordStatus;
import dev.dimi.erp.financeiro.internal.application.FinancialRecordSummary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response de MVF com parcelas e rateios. */
public record FinancialRecordApiResponse(
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
    List<InstallmentApiResponse> installments,
    List<ApportionmentApiResponse> apportionments) {

  public static FinancialRecordApiResponse from(FinancialRecordSummary s) {
    return new FinancialRecordApiResponse(
        s.id(),
        s.version(),
        s.number(),
        s.branchId(),
        s.direction(),
        s.counterpartyId(),
        s.documentNumber(),
        s.issuedAt(),
        s.totalValue(),
        s.status(),
        s.originType(),
        s.originId(),
        s.notes(),
        s.idempotencyKey(),
        s.createdAt(),
        s.updatedAt(),
        s.installments().stream().map(InstallmentApiResponse::from).toList(),
        s.apportionments().stream().map(ApportionmentApiResponse::from).toList());
  }
}
