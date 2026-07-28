package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.Priority;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.compras.internal.application.PurchaseRequestSummary;
import dev.dimi.erp.identidade.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response de SC com linhas. */
public record PurchaseRequestApiResponse(
    PurchaseRequestId id,
    long version,
    long number,
    BranchId branchId,
    UserId requestedBy,
    UUID departmentId,
    UUID costCenterId,
    LocalDate neededBy,
    Priority priority,
    PurchaseRequestStatus status,
    String justification,
    UserId approvedBy,
    Instant approvedAt,
    UserId rejectedBy,
    Instant rejectedAt,
    String rejectionReason,
    Instant createdAt,
    Instant updatedAt,
    List<PurchaseRequestItemApiResponse> items) {

  public static PurchaseRequestApiResponse from(PurchaseRequestSummary s) {
    return new PurchaseRequestApiResponse(
        s.id(),
        s.version(),
        s.number(),
        s.branchId(),
        s.requestedBy(),
        s.departmentId(),
        s.costCenterId(),
        s.neededBy(),
        s.priority(),
        s.status(),
        s.justification(),
        s.approvedBy(),
        s.approvedAt(),
        s.rejectedBy(),
        s.rejectedAt(),
        s.rejectionReason(),
        s.createdAt(),
        s.updatedAt(),
        s.items().stream().map(PurchaseRequestItemApiResponse::from).toList());
  }
}
