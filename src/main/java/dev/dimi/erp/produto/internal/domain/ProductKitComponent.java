package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
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
 * Componente de um kit. {@code kitPackagingId} é o SKU "pai" (kit); {@code componentPackagingId} é
 * o SKU consumido na explosão (RN-PRD-006). Quantidade refere-se à unidade do componente.
 *
 * <p>DDL impede auto-referência (kit ≠ componente) e quantidade negativa.
 */
@Entity
@Table(name = "product_kit_component")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "kitPackagingId", "componentPackagingId", "quantity"})
public class ProductKitComponent {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "kit_packaging_id", nullable = false, updatable = false)
  private UUID kitPackagingId;

  @Column(name = "component_packaging_id", nullable = false, updatable = false)
  private UUID componentPackagingId;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public KitComponentId id() {
    return new KitComponentId(id);
  }

  public static ProductKitComponent create(
      UUID kitPackagingId, UUID componentPackagingId, BigDecimal quantity) {
    Objects.requireNonNull(kitPackagingId, "kitPackagingId");
    Objects.requireNonNull(componentPackagingId, "componentPackagingId");
    Objects.requireNonNull(quantity, "quantity");
    if (kitPackagingId.equals(componentPackagingId)) {
      throw new IllegalArgumentException("kit não pode referenciar a si mesmo");
    }
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity deve ser positivo");
    }
    ProductKitComponent c = new ProductKitComponent();
    c.id = UuidCreator.getTimeOrderedEpoch();
    c.kitPackagingId = kitPackagingId;
    c.componentPackagingId = componentPackagingId;
    c.quantity = quantity;
    return c;
  }

  public void changeQuantity(BigDecimal newQuantity) {
    if (newQuantity != null && newQuantity.signum() > 0) {
      this.quantity = newQuantity;
    }
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
    if (!(o instanceof ProductKitComponent other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
