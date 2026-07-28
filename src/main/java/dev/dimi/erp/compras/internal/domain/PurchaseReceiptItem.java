package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
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
 * Linha do recebimento. {@code unitCost} alimenta WAC do SKU (RN-COM-016) via {@code
 * StockInventoryPort.receive} no slice 6.2. Custo real pode diferir do {@code unit_price} do PC —
 * frete rateado, preço renegociado etc.
 */
@Entity
@Table(name = "purchase_receipt_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "receiptId", "orderItemId", "quantity", "unitCost"})
public class PurchaseReceiptItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "receipt_id", nullable = false, updatable = false)
  private UUID receiptId;

  @Column(name = "order_item_id", nullable = false, updatable = false)
  private UUID orderItemId;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_cost", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal unitCost;

  @Column(name = "batch_id", updatable = false)
  private UUID batchId;

  @Column(name = "location_id", updatable = false)
  private UUID locationId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PurchaseReceiptItemId id() {
    return new PurchaseReceiptItemId(id);
  }

  public PurchaseReceiptId receiptIdTyped() {
    return new PurchaseReceiptId(receiptId);
  }

  public PurchaseOrderItemId orderItemIdTyped() {
    return new PurchaseOrderItemId(orderItemId);
  }

  public StockBatchId batchIdTyped() {
    return batchId == null ? null : new StockBatchId(batchId);
  }

  public StockLocationId locationIdTyped() {
    return locationId == null ? null : new StockLocationId(locationId);
  }

  public static PurchaseReceiptItem create(
      UUID receiptId,
      UUID orderItemId,
      BigDecimal quantity,
      BigDecimal unitCost,
      UUID batchId,
      UUID locationId) {
    PurchaseReceiptItem i = new PurchaseReceiptItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.receiptId = Objects.requireNonNull(receiptId, "receiptId");
    i.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
    i.quantity = Objects.requireNonNull(quantity, "quantity");
    i.unitCost = Objects.requireNonNull(unitCost, "unitCost");
    i.batchId = batchId;
    i.locationId = locationId;
    return i;
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
    if (!(o instanceof PurchaseReceiptItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
