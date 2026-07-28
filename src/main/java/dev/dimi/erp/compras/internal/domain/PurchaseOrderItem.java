package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
 * Linha de PC. {@code quantityReceived} acumula recebimentos parciais (RN-COM-013); slice 6.2
 * recalcula no service ao processar receipt. PC progride pra PARTIALLY_RECEIVED/RECEIVED conforme
 * essa soma.
 */
@Entity
@Table(name = "purchase_order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(
    of = {"id", "orderId", "lineNumber", "packagingId", "quantityOrdered", "quantityReceived"})
public class PurchaseOrderItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "quantity_ordered", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal quantityOrdered;

  @Column(name = "quantity_received", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantityReceived;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountValue;

  @Column(name = "net_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal netValue;

  @Column(name = "expected_delivery")
  private LocalDate expectedDelivery;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PurchaseOrderItemId id() {
    return new PurchaseOrderItemId(id);
  }

  public PurchaseOrderId orderIdTyped() {
    return new PurchaseOrderId(orderId);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public BigDecimal quantityPending() {
    return quantityOrdered.subtract(quantityReceived);
  }

  public boolean isFullyReceived() {
    return quantityReceived.compareTo(quantityOrdered) >= 0;
  }

  public static PurchaseOrderItem create(
      UUID orderId,
      int lineNumber,
      UUID packagingId,
      BigDecimal quantityOrdered,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      BigDecimal netValue,
      LocalDate expectedDelivery) {
    PurchaseOrderItem i = new PurchaseOrderItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.orderId = Objects.requireNonNull(orderId, "orderId");
    i.lineNumber = lineNumber;
    i.packagingId = Objects.requireNonNull(packagingId, "packagingId");
    i.quantityOrdered = Objects.requireNonNull(quantityOrdered, "quantityOrdered");
    i.quantityReceived = BigDecimal.ZERO;
    i.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    i.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    i.netValue = Objects.requireNonNull(netValue, "netValue");
    i.expectedDelivery = expectedDelivery;
    return i;
  }

  /** Service do slice 6.2 chama isto ao processar receipt. */
  public void addReceived(BigDecimal additional) {
    Objects.requireNonNull(additional, "additional");
    this.quantityReceived = this.quantityReceived.add(additional);
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PurchaseOrderItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
