package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Cotação. Pode ter origem em uma SC ({@code requestId}) ou ser ad-hoc (RN-COM-005). Fornecedores
 * convidados ficam em {@link QuotationSupplier} (composite PK); respostas item-a-item em {@link
 * QuotationResponse}.
 */
@Entity
@Table(name = "quotation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "branchId", "status"})
public class Quotation {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "request_id", updatable = false)
  private UUID requestId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QuotationStatus status;

  @Column(name = "deadline")
  private LocalDate deadline;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public QuotationId id() {
    return new QuotationId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PurchaseRequestId requestIdTyped() {
    return requestId == null ? null : new PurchaseRequestId(requestId);
  }

  public static Quotation open(long number, UUID branchId, UUID requestId, LocalDate deadline) {
    Quotation q = new Quotation();
    q.id = UuidCreator.getTimeOrderedEpoch();
    q.number = number;
    q.branchId = Objects.requireNonNull(branchId, "branchId");
    q.requestId = requestId;
    q.deadline = deadline;
    q.status = QuotationStatus.OPEN;
    return q;
  }

  public void markSent() {
    this.status = QuotationStatus.SENT;
  }

  public void markReceiving() {
    this.status = QuotationStatus.RECEIVING;
  }

  public void close() {
    this.status = QuotationStatus.CLOSED;
    this.closedAt = Instant.now();
  }

  public void cancel() {
    this.status = QuotationStatus.CANCELLED;
  }

  public void updateDeadline(LocalDate deadline) {
    if (deadline != null) this.deadline = deadline;
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
    if (!(o instanceof Quotation other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
