package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.produto.PackagingId;
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
 * SKU final que viaja entre módulos (venda, NFe, compra, estoque). Toda movimentação é por {@code
 * packaging_id}, nunca por {@link Product#id()} (RN-PRD-001).
 *
 * <p>{@code conversionFactor} converte da unidade comercial para a unidade fiscal base — relevante
 * para emissão de NFe e cálculo de saldo agregado quando o tenant trabalha com caixas vs unidades.
 */
@Entity
@Table(name = "packaging")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "variantId", "sku", "isDefault", "active"})
public class Packaging {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "variant_id", nullable = false, updatable = false)
  private UUID variantId;

  @Column(name = "sku", nullable = false, updatable = false)
  private String sku;

  @Column(name = "barcode")
  private String barcode;

  @Column(name = "commercial_unit_id")
  private UUID commercialUnitId;

  @Column(name = "fiscal_unit_id")
  private UUID fiscalUnitId;

  @Column(name = "conversion_factor", nullable = false, precision = 15, scale = 4)
  private BigDecimal conversionFactor;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "weight_kg", precision = 10, scale = 3)
  private BigDecimal weightKg;

  @Column(name = "deactivated_at")
  private Instant deactivatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public PackagingId id() {
    return new PackagingId(id);
  }

  public VariantId variantIdTyped() {
    return new VariantId(variantId);
  }

  public static Packaging create(
      UUID variantId,
      String sku,
      String barcode,
      UUID commercialUnitId,
      UUID fiscalUnitId,
      BigDecimal conversionFactor,
      boolean isDefault,
      BigDecimal weightKg) {
    Packaging p = new Packaging();
    p.id = UuidCreator.getTimeOrderedEpoch();
    p.variantId = Objects.requireNonNull(variantId, "variantId");
    p.sku = Objects.requireNonNull(sku, "sku");
    p.barcode = barcode;
    p.commercialUnitId = commercialUnitId;
    p.fiscalUnitId = fiscalUnitId;
    p.conversionFactor = conversionFactor == null ? BigDecimal.ONE : conversionFactor;
    p.isDefault = isDefault;
    p.weightKg = weightKg;
    p.active = true;
    return p;
  }

  public void update(
      String barcode,
      UUID commercialUnitId,
      UUID fiscalUnitId,
      BigDecimal conversionFactor,
      BigDecimal weightKg) {
    if (barcode != null) this.barcode = barcode;
    if (commercialUnitId != null) this.commercialUnitId = commercialUnitId;
    if (fiscalUnitId != null) this.fiscalUnitId = fiscalUnitId;
    if (conversionFactor != null) this.conversionFactor = conversionFactor;
    if (weightKg != null) this.weightKg = weightKg;
  }

  public void markDefault() {
    this.isDefault = true;
  }

  public void unmarkDefault() {
    this.isDefault = false;
  }

  public void deactivate() {
    this.active = false;
    this.deactivatedAt = Instant.now();
  }

  public void reactivate() {
    this.active = true;
    this.deactivatedAt = null;
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
    if (!(o instanceof Packaging other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
