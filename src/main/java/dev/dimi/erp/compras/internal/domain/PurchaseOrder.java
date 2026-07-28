package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseOrderStatus;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
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
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Pedido de Compra. Pode ser originado de cotação (RN-COM-008) ou ad-hoc (RN-COM-009). Status
 * progride OPEN → SENT → PARTIALLY_RECEIVED → RECEIVED; CANCELLED pode acontecer em qualquer ponto
 * antes de RECEIVED (RN-COM-010).
 */
@Entity
@Table(name = "purchase_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "branchId", "supplierId", "status", "total"})
public class PurchaseOrder {

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

  @Column(name = "supplier_id", nullable = false, updatable = false)
  private UUID supplierId;

  @Column(name = "quotation_id", updatable = false)
  private UUID quotationId;

  @Column(name = "request_id", updatable = false)
  private UUID requestId;

  @Column(name = "payment_condition_id")
  private UUID paymentConditionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private PurchaseOrderStatus status;

  @Column(name = "delivery_date_expected")
  private LocalDate deliveryDateExpected;

  @Column(name = "total_products", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalProducts;

  @Column(name = "total_freight", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalFreight;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public PurchaseOrderId id() {
    return new PurchaseOrderId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId supplierIdTyped() {
    return new PersonId(supplierId);
  }

  public QuotationId quotationIdTyped() {
    return quotationId == null ? null : new QuotationId(quotationId);
  }

  public PurchaseRequestId requestIdTyped() {
    return requestId == null ? null : new PurchaseRequestId(requestId);
  }

  public static PurchaseOrder open(
      long number,
      UUID branchId,
      UUID supplierId,
      UUID quotationId,
      UUID requestId,
      UUID paymentConditionId,
      LocalDate deliveryDateExpected,
      String notes) {
    PurchaseOrder o = new PurchaseOrder();
    o.id = UuidCreator.getTimeOrderedEpoch();
    o.number = number;
    o.branchId = Objects.requireNonNull(branchId, "branchId");
    o.supplierId = Objects.requireNonNull(supplierId, "supplierId");
    o.quotationId = quotationId;
    o.requestId = requestId;
    o.paymentConditionId = paymentConditionId;
    o.status = PurchaseOrderStatus.OPEN;
    o.deliveryDateExpected = deliveryDateExpected;
    o.totalProducts = BigDecimal.ZERO;
    o.totalFreight = BigDecimal.ZERO;
    o.total = BigDecimal.ZERO;
    o.notes = notes;
    return o;
  }

  public void markSent() {
    this.status = PurchaseOrderStatus.SENT;
  }

  public void markPartiallyReceived() {
    this.status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
  }

  public void markReceived() {
    this.status = PurchaseOrderStatus.RECEIVED;
  }

  public void cancel() {
    this.status = PurchaseOrderStatus.CANCELLED;
  }

  public void updateTotals(BigDecimal totalProducts, BigDecimal totalFreight, BigDecimal total) {
    if (totalProducts != null) this.totalProducts = totalProducts;
    if (totalFreight != null) this.totalFreight = totalFreight;
    if (total != null) this.total = total;
  }

  public void updateMetadata(
      UUID paymentConditionId, LocalDate deliveryDateExpected, String notes) {
    if (paymentConditionId != null) this.paymentConditionId = paymentConditionId;
    if (deliveryDateExpected != null) this.deliveryDateExpected = deliveryDateExpected;
    if (notes != null) this.notes = notes;
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
    if (!(o instanceof PurchaseOrder other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
