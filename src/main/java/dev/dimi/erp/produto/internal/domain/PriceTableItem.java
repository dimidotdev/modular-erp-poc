package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceTableId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Item de tabela de preço — par (packaging, sale_price) com desconto máximo permitido. {@code
 * discountMax} é fração (0.1000 = 10%), enforced em vendas pelo módulo movimentação (RN-PRD-005).
 */
@Entity
@Table(name = "price_table_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "priceTableId", "packagingId", "salePrice", "discountMax"})
public class PriceTableItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "price_table_id", nullable = false, updatable = false)
  private UUID priceTableId;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "sale_price", nullable = false, precision = 15, scale = 2)
  private BigDecimal salePrice;

  @Column(name = "discount_max", nullable = false, precision = 7, scale = 4)
  private BigDecimal discountMax;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public PriceTableItemId id() {
    return new PriceTableItemId(id);
  }

  public PriceTableId priceTableIdTyped() {
    return new PriceTableId(priceTableId);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public static PriceTableItem create(
      UUID priceTableId, UUID packagingId, BigDecimal salePrice, BigDecimal discountMax) {
    Objects.requireNonNull(priceTableId, "priceTableId");
    Objects.requireNonNull(packagingId, "packagingId");
    Objects.requireNonNull(salePrice, "salePrice");
    if (salePrice.signum() < 0) {
      throw new IllegalArgumentException("salePrice não pode ser negativo");
    }
    BigDecimal effectiveDiscount = discountMax == null ? BigDecimal.ZERO : discountMax;
    if (effectiveDiscount.signum() < 0 || effectiveDiscount.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("discountMax deve estar entre 0 e 1");
    }
    PriceTableItem item = new PriceTableItem();
    item.id = UuidCreator.getTimeOrderedEpoch();
    item.priceTableId = priceTableId;
    item.packagingId = packagingId;
    item.salePrice = salePrice;
    item.discountMax = effectiveDiscount;
    return item;
  }

  public void changePrice(BigDecimal newSalePrice, BigDecimal newDiscountMax) {
    if (newSalePrice != null) {
      if (newSalePrice.signum() < 0) {
        throw new IllegalArgumentException("salePrice não pode ser negativo");
      }
      this.salePrice = newSalePrice;
    }
    if (newDiscountMax != null) {
      if (newDiscountMax.signum() < 0 || newDiscountMax.compareTo(BigDecimal.ONE) > 0) {
        throw new IllegalArgumentException("discountMax deve estar entre 0 e 1");
      }
      this.discountMax = newDiscountMax;
    }
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
    if (!(o instanceof PriceTableItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
