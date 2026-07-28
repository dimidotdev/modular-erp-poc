package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.SalesOrderId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Venda — documento de saída efetiva. Criada em {@link SaleStatus#OPEN} (mutável); {@code close}
 * baixa estoque e gera MVF (RN-MOV-004), tornando-a imutável (RN-MOV-008).
 *
 * <p>Foundation 5.1: persistência + transições de status simples. Idempotência de close via {@code
 * idempotency_key} é enforced pelo UNIQUE index parcial — o service do slice 5.2 trata conflito
 * devolvendo a venda existente.
 */
@Entity
@Table(name = "sale")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "branchId", "customerId", "number", "status", "total"})
public class Sale {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "salesperson_id")
  private UUID salespersonId;

  @Column(name = "sales_order_id", updatable = false)
  private UUID salesOrderId;

  @Column(name = "payment_condition_id")
  private UUID paymentConditionId;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SaleStatus status;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "total_gross", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalGross;

  @Column(name = "total_discount", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalDiscount;

  @Column(name = "total_freight", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalFreight;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "notes")
  private String notes;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public SaleId id() {
    return new SaleId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId customerIdTyped() {
    return new PersonId(customerId);
  }

  public SalesOrderId salesOrderIdTyped() {
    return salesOrderId == null ? null : new SalesOrderId(salesOrderId);
  }

  /** Cria venda OPEN. Idempotency-key obrigatório no fechamento, opcional na criação inicial. */
  public static Sale open(
      UUID branchId,
      UUID customerId,
      UUID salespersonId,
      UUID salesOrderId,
      UUID paymentConditionId,
      long number,
      String idempotencyKey,
      String notes) {
    Sale s = new Sale();
    s.id = UuidCreator.getTimeOrderedEpoch();
    s.branchId = Objects.requireNonNull(branchId, "branchId");
    s.customerId = Objects.requireNonNull(customerId, "customerId");
    s.salespersonId = salespersonId;
    s.salesOrderId = salesOrderId;
    s.paymentConditionId = paymentConditionId;
    s.number = number;
    s.status = SaleStatus.OPEN;
    s.issuedAt = Instant.now();
    s.totalGross = BigDecimal.ZERO;
    s.totalDiscount = BigDecimal.ZERO;
    s.totalFreight = BigDecimal.ZERO;
    s.total = BigDecimal.ZERO;
    s.idempotencyKey = idempotencyKey;
    s.notes = notes;
    return s;
  }

  public void close() {
    this.status = SaleStatus.CLOSED;
    this.closedAt = Instant.now();
  }

  public void markCancellationInProgress() {
    this.status = SaleStatus.CANCELLATION_IN_PROGRESS;
  }

  public void markCancelled(String reason) {
    this.status = SaleStatus.CANCELLED;
    this.cancelledAt = Instant.now();
    this.cancellationReason = reason;
  }

  public void markPartialCancellationFailed() {
    this.status = SaleStatus.PARTIAL_CANCELLATION_FAILED;
  }

  public void markLockedForCancellation() {
    this.status = SaleStatus.LOCKED_FOR_CANCELLATION;
  }

  public void updateTotals(
      BigDecimal totalGross, BigDecimal totalDiscount, BigDecimal totalFreight, BigDecimal total) {
    if (totalGross != null) this.totalGross = totalGross;
    if (totalDiscount != null) this.totalDiscount = totalDiscount;
    if (totalFreight != null) this.totalFreight = totalFreight;
    if (total != null) this.total = total;
  }

  public void attachInvoice(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }

  public void updateNotes(String notes) {
    if (notes != null) this.notes = notes;
  }

  public void updateSalesperson(UUID salespersonId) {
    this.salespersonId = salespersonId;
  }

  public void updatePaymentCondition(UUID paymentConditionId) {
    this.paymentConditionId = paymentConditionId;
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
    if (!(o instanceof Sale other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
