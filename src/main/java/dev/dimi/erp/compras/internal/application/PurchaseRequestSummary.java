package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.Priority;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.compras.internal.domain.PurchaseRequest;
import dev.dimi.erp.identidade.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Snapshot de SC com itens. */
public record PurchaseRequestSummary(
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
    List<PurchaseRequestItemSummary> items) {

  public static PurchaseRequestSummary of(
      PurchaseRequest r, List<PurchaseRequestItemSummary> items) {
    return new PurchaseRequestSummary(
        r.id(),
        r.getVersion(),
        r.getNumber(),
        r.branchIdTyped(),
        r.requestedByTyped(),
        r.getDepartmentId(),
        r.getCostCenterId(),
        r.getNeededBy(),
        r.getPriority(),
        r.getStatus(),
        r.getJustification(),
        r.approvedByTyped(),
        r.getApprovedAt(),
        r.rejectedByTyped(),
        r.getRejectedAt(),
        r.getRejectionReason(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        items);
  }
}
