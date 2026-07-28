package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
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

/**
 * Linha de {@link InventoryAdjustment}: (expected, counted, delta) por (packaging, location).
 * {@code delta} é calculado em Java no construtor ({@code counted - expected}). O DDL (V11 + V12)
 * começa com {@code GENERATED ALWAYS STORED} e depois converte para coluna regular — não funcionava
 * bem com Hibernate pós-INSERT.
 */
@Entity
@Table(name = "stock_inventory_adjustment_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(
    of = {"id", "adjustmentId", "packagingId", "locationId", "expectedQty", "countedQty", "delta"})
public class InventoryAdjustmentItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "adjustment_id", nullable = false, updatable = false)
  private UUID adjustmentId;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "location_id", nullable = false, updatable = false)
  private UUID locationId;

  @Column(name = "expected_qty", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal expectedQty;

  @Column(name = "counted_qty", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal countedQty;

  /** Calculado no construtor; coluna comum em Postgres após V12 (DROP EXPRESSION). */
  @Column(name = "delta", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal delta;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public InventoryAdjustmentItemId id() {
    return new InventoryAdjustmentItemId(id);
  }

  public InventoryAdjustmentId adjustmentIdTyped() {
    return new InventoryAdjustmentId(adjustmentId);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public StockLocationId locationIdTyped() {
    return new StockLocationId(locationId);
  }

  public static InventoryAdjustmentItem create(
      UUID adjustmentId,
      UUID packagingId,
      UUID locationId,
      BigDecimal expectedQty,
      BigDecimal countedQty) {
    InventoryAdjustmentItem i = new InventoryAdjustmentItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.adjustmentId = Objects.requireNonNull(adjustmentId, "adjustmentId");
    i.packagingId = Objects.requireNonNull(packagingId, "packagingId");
    i.locationId = Objects.requireNonNull(locationId, "locationId");
    i.expectedQty = Objects.requireNonNull(expectedQty, "expectedQty");
    i.countedQty = Objects.requireNonNull(countedQty, "countedQty");
    i.delta = countedQty.subtract(expectedQty);
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
    if (!(o instanceof InventoryAdjustmentItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
