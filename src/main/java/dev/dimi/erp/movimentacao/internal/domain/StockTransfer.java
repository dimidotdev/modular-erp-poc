package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.movimentacao.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Transferência entre filiais ou locais. CLOSED dispara o par atômico via {@code
 * StockInventoryPort.transfer} no slice 5.3 + NFe quando entre CNPJs distintos (RN-MOV-019/020).
 */
@Entity
@Table(name = "stock_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "originBranchId", "destBranchId", "status"})
public class StockTransfer {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "origin_branch_id", nullable = false, updatable = false)
  private UUID originBranchId;

  @Column(name = "dest_branch_id", nullable = false, updatable = false)
  private UUID destBranchId;

  @Column(name = "origin_location_id")
  private UUID originLocationId;

  @Column(name = "dest_location_id")
  private UUID destLocationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TransferStatus status;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public StockTransferId id() {
    return new StockTransferId(id);
  }

  public BranchId originBranchIdTyped() {
    return new BranchId(originBranchId);
  }

  public BranchId destBranchIdTyped() {
    return new BranchId(destBranchId);
  }

  public StockLocationId originLocationIdTyped() {
    return originLocationId == null ? null : new StockLocationId(originLocationId);
  }

  public StockLocationId destLocationIdTyped() {
    return destLocationId == null ? null : new StockLocationId(destLocationId);
  }

  public static StockTransfer open(
      long number,
      UUID originBranchId,
      UUID destBranchId,
      UUID originLocationId,
      UUID destLocationId,
      String notes) {
    StockTransfer t = new StockTransfer();
    t.id = UuidCreator.getTimeOrderedEpoch();
    t.number = number;
    t.originBranchId = Objects.requireNonNull(originBranchId, "originBranchId");
    t.destBranchId = Objects.requireNonNull(destBranchId, "destBranchId");
    t.originLocationId = originLocationId;
    t.destLocationId = destLocationId;
    t.status = TransferStatus.OPEN;
    t.issuedAt = Instant.now();
    t.notes = notes;
    return t;
  }

  public void close() {
    this.status = TransferStatus.CLOSED;
    this.closedAt = Instant.now();
  }

  public void cancel() {
    this.status = TransferStatus.CANCELLED;
  }

  public void attachInvoice(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }

  public void updateNotes(String notes) {
    if (notes != null) this.notes = notes;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (issuedAt == null) {
      issuedAt = now;
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
    if (!(o instanceof StockTransfer other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
