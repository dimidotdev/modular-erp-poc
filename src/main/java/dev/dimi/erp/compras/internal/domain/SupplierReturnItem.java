package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.compras.SupplierReturnId;
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

/** Linha de devolução a fornecedor. Carrega o {@code unitCost} usado pra estornar MVF. */
@Entity
@Table(name = "supplier_return_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "supplierReturnId", "receiptItemId", "quantity", "unitCost"})
public class SupplierReturnItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "supplier_return_id", nullable = false, updatable = false)
  private UUID supplierReturnId;

  @Column(name = "receipt_item_id", nullable = false, updatable = false)
  private UUID receiptItemId;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_cost", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal unitCost;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public SupplierReturnItemId id() {
    return new SupplierReturnItemId(id);
  }

  public SupplierReturnId supplierReturnIdTyped() {
    return new SupplierReturnId(supplierReturnId);
  }

  public PurchaseReceiptItemId receiptItemIdTyped() {
    return new PurchaseReceiptItemId(receiptItemId);
  }

  public static SupplierReturnItem create(
      UUID supplierReturnId, UUID receiptItemId, BigDecimal quantity, BigDecimal unitCost) {
    SupplierReturnItem i = new SupplierReturnItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.supplierReturnId = Objects.requireNonNull(supplierReturnId, "supplierReturnId");
    i.receiptItemId = Objects.requireNonNull(receiptItemId, "receiptItemId");
    i.quantity = Objects.requireNonNull(quantity, "quantity");
    i.unitCost = Objects.requireNonNull(unitCost, "unitCost");
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
    if (!(o instanceof SupplierReturnItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
