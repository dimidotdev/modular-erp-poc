package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.Priority;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Solicitação de Compra (SC). Pode ser submetida pra aprovação ou aprovada diretamente
 * (RN-COM-001/002). {@code costCenterId} fica como UUID cru até módulo financeiro existir.
 */
@Entity
@Table(name = "purchase_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "branchId", "number", "status", "priority"})
public class PurchaseRequest {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "requested_by", nullable = false, updatable = false)
  private UUID requestedBy;

  @Column(name = "department_id")
  private UUID departmentId;

  @Column(name = "cost_center_id")
  private UUID costCenterId;

  @Column(name = "needed_by")
  private LocalDate neededBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false)
  private Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PurchaseRequestStatus status;

  @Column(name = "justification")
  private String justification;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejected_by")
  private UUID rejectedBy;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public PurchaseRequestId id() {
    return new PurchaseRequestId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public UserId requestedByTyped() {
    return new UserId(requestedBy);
  }

  public UserId approvedByTyped() {
    return approvedBy == null ? null : new UserId(approvedBy);
  }

  public UserId rejectedByTyped() {
    return rejectedBy == null ? null : new UserId(rejectedBy);
  }

  public static PurchaseRequest draft(
      long number,
      UUID branchId,
      UUID requestedBy,
      UUID departmentId,
      UUID costCenterId,
      LocalDate neededBy,
      Priority priority,
      String justification) {
    PurchaseRequest r = new PurchaseRequest();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.number = number;
    r.branchId = Objects.requireNonNull(branchId, "branchId");
    r.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy");
    r.departmentId = departmentId;
    r.costCenterId = costCenterId;
    r.neededBy = neededBy;
    r.priority = priority == null ? Priority.NORMAL : priority;
    r.justification = justification;
    r.status = PurchaseRequestStatus.DRAFT;
    return r;
  }

  public void submit() {
    this.status = PurchaseRequestStatus.PENDING_APPROVAL;
  }

  public void approve(UUID by) {
    this.status = PurchaseRequestStatus.APPROVED;
    this.approvedBy = by;
    this.approvedAt = Instant.now();
  }

  public void reject(UUID by, String reason) {
    this.status = PurchaseRequestStatus.REJECTED;
    this.rejectedBy = by;
    this.rejectedAt = Instant.now();
    this.rejectionReason = reason;
  }

  public void markQuoting() {
    this.status = PurchaseRequestStatus.QUOTING;
  }

  public void markOrdered() {
    this.status = PurchaseRequestStatus.ORDERED;
  }

  public void cancel() {
    this.status = PurchaseRequestStatus.CANCELLED;
  }

  public void updateMetadata(
      UUID departmentId,
      UUID costCenterId,
      LocalDate neededBy,
      Priority priority,
      String justification) {
    if (departmentId != null) this.departmentId = departmentId;
    if (costCenterId != null) this.costCenterId = costCenterId;
    if (neededBy != null) this.neededBy = neededBy;
    if (priority != null) this.priority = priority;
    if (justification != null) this.justification = justification;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PurchaseRequest other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
