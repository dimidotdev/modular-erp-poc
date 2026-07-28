package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.movimentacao.SaleReturnId;
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

/**
 * Item de devolução. RN-MOV-014: três quantidades:
 *
 * <ul>
 *   <li>{@code quantityReturned} — volta ao estoque (gera INBOUND).
 *   <li>{@code quantityLost} — perda total, não retorna mas reduz a dívida do cliente.
 *   <li>{@code quantityUsed} — cliente consumiu, sem refund nem estoque.
 * </ul>
 *
 * Soma ≤ {@code sale_item.quantity} é enforced no service (CHECK com subquery não suportado em
 * Postgres). RN-MOV-016: {@code refundValue = (returned + lost) × unit_price}.
 */
@Entity
@Table(name = "sale_return_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(
    of = {
      "id",
      "returnId",
      "saleItemId",
      "quantityReturned",
      "quantityLost",
      "quantityUsed",
      "refundValue"
    })
public class SaleReturnItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "return_id", nullable = false, updatable = false)
  private UUID returnId;

  @Column(name = "sale_item_id", nullable = false, updatable = false)
  private UUID saleItemId;

  @Column(name = "quantity_returned", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantityReturned;

  @Column(name = "quantity_lost", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantityLost;

  @Column(name = "quantity_used", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantityUsed;

  @Column(name = "refund_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal refundValue;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public SaleReturnItemId id() {
    return new SaleReturnItemId(id);
  }

  public SaleReturnId returnIdTyped() {
    return new SaleReturnId(returnId);
  }

  public SaleItemId saleItemIdTyped() {
    return new SaleItemId(saleItemId);
  }

  public static SaleReturnItem create(
      UUID returnId,
      UUID saleItemId,
      BigDecimal quantityReturned,
      BigDecimal quantityLost,
      BigDecimal quantityUsed,
      BigDecimal refundValue) {
    SaleReturnItem i = new SaleReturnItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.returnId = Objects.requireNonNull(returnId, "returnId");
    i.saleItemId = Objects.requireNonNull(saleItemId, "saleItemId");
    i.quantityReturned = quantityReturned == null ? BigDecimal.ZERO : quantityReturned;
    i.quantityLost = quantityLost == null ? BigDecimal.ZERO : quantityLost;
    i.quantityUsed = quantityUsed == null ? BigDecimal.ZERO : quantityUsed;
    i.refundValue = refundValue == null ? BigDecimal.ZERO : refundValue;
    return i;
  }

  public void updateQuantities(
      BigDecimal quantityReturned,
      BigDecimal quantityLost,
      BigDecimal quantityUsed,
      BigDecimal refundValue) {
    if (quantityReturned != null) this.quantityReturned = quantityReturned;
    if (quantityLost != null) this.quantityLost = quantityLost;
    if (quantityUsed != null) this.quantityUsed = quantityUsed;
    if (refundValue != null) this.refundValue = refundValue;
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
    if (!(o instanceof SaleReturnItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
