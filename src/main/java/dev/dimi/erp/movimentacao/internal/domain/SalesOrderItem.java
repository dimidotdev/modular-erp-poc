package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/** Linha de {@link SalesOrder}. Quantidade, preço unit e desconto na linha. */
@Entity
@Table(name = "sales_order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "orderId", "lineNumber", "packagingId", "quantity", "netValue"})
public class SalesOrderItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "discount_pct", nullable = false, precision = 7, scale = 4)
  private BigDecimal discountPct;

  @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountValue;

  @Column(name = "net_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal netValue;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public SalesOrderItemId id() {
    return new SalesOrderItemId(id);
  }

  public SalesOrderId orderIdTyped() {
    return new SalesOrderId(orderId);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public static SalesOrderItem create(
      UUID orderId,
      int lineNumber,
      UUID packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountPct,
      BigDecimal discountValue,
      BigDecimal netValue) {
    SalesOrderItem i = new SalesOrderItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.orderId = Objects.requireNonNull(orderId, "orderId");
    i.lineNumber = lineNumber;
    i.packagingId = Objects.requireNonNull(packagingId, "packagingId");
    i.quantity = Objects.requireNonNull(quantity, "quantity");
    i.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    i.discountPct = discountPct == null ? BigDecimal.ZERO : discountPct;
    i.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    i.netValue = Objects.requireNonNull(netValue, "netValue");
    return i;
  }

  public void update(
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountPct,
      BigDecimal discountValue,
      BigDecimal netValue) {
    if (quantity != null) this.quantity = quantity;
    if (unitPrice != null) this.unitPrice = unitPrice;
    if (discountPct != null) this.discountPct = discountPct;
    if (discountValue != null) this.discountValue = discountValue;
    if (netValue != null) this.netValue = netValue;
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
    if (!(o instanceof SalesOrderItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
