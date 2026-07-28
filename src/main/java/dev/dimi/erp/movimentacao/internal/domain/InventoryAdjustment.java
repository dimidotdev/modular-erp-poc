package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Documento de ajuste por conferência (RN-MOV-024). Diferente do ajuste manual direto via {@code
 * StockInventoryPort.adjust}: este é batch com {@code expected_qty} vs {@code counted_qty} por
 * linha — útil para inventários físicos periódicos. Exige step-up + auditoria reforçada
 * (RN-MOV-025).
 */
@Entity
@Table(name = "stock_inventory_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "branchId", "userId"})
public class InventoryAdjustment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "notes")
  private String notes;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public InventoryAdjustmentId id() {
    return new InventoryAdjustmentId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public UserId userIdTyped() {
    return new UserId(userId);
  }

  public static InventoryAdjustment record(
      long number, UUID branchId, String reason, String notes, UUID userId) {
    InventoryAdjustment a = new InventoryAdjustment();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.number = number;
    a.branchId = Objects.requireNonNull(branchId, "branchId");
    a.issuedAt = Instant.now();
    a.reason = Objects.requireNonNull(reason, "reason");
    a.notes = notes;
    a.userId = Objects.requireNonNull(userId, "userId");
    return a;
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
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof InventoryAdjustment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
