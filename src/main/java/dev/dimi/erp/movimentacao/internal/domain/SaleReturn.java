package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.movimentacao.ReturnStatus;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleReturnId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Devolução de venda. Referencia {@code sale.id} (RN-MOV-013). Cada item ({@link SaleReturnItem})
 * tem 3 quantidades (returned/lost/used) — RN-MOV-014. CLOSED dispara estoque/MVF/NFe no slice 5.3.
 */
@Entity
@Table(name = "sale_return")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "saleId", "branchId", "status", "total"})
public class SaleReturn {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "sale_id", nullable = false, updatable = false)
  private UUID saleId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ReturnStatus status;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public SaleReturnId id() {
    return new SaleReturnId(id);
  }

  public SaleId saleIdTyped() {
    return new SaleId(saleId);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public static SaleReturn open(UUID saleId, UUID branchId, String reason, String notes) {
    SaleReturn r = new SaleReturn();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.saleId = Objects.requireNonNull(saleId, "saleId");
    r.branchId = Objects.requireNonNull(branchId, "branchId");
    r.status = ReturnStatus.OPEN;
    r.issuedAt = Instant.now();
    r.reason = Objects.requireNonNull(reason, "reason");
    r.total = BigDecimal.ZERO;
    r.notes = notes;
    return r;
  }

  public void close() {
    this.status = ReturnStatus.CLOSED;
    this.closedAt = Instant.now();
  }

  public void cancel() {
    this.status = ReturnStatus.CANCELLED;
  }

  public void updateTotal(BigDecimal total) {
    if (total != null) this.total = total;
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
    if (!(o instanceof SaleReturn other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
