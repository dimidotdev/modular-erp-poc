package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.produto.ProductId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Variação concreta de um {@link Product} ("Camiseta polo azul M"). Quando produto não tem
 * variedades explícitas, uma variant default é criada automaticamente (RN-PRD-003) — sentinela
 * invisível para sustentar o pivot product→packaging.
 *
 * <p>Embalagens são FK aqui (não navegação eager).
 */
@Entity
@Table(name = "variant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "productId", "code", "isDefault", "active"})
public class Variant {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "deactivated_at")
  private Instant deactivatedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public VariantId id() {
    return new VariantId(id);
  }

  public ProductId productIdTyped() {
    return new ProductId(productId);
  }

  public static Variant create(UUID productId, String code, String name, boolean isDefault) {
    Variant v = new Variant();
    v.id = UuidCreator.getTimeOrderedEpoch();
    v.productId = Objects.requireNonNull(productId, "productId");
    v.code = Objects.requireNonNull(code, "code");
    v.name = Objects.requireNonNull(name, "name");
    v.isDefault = isDefault;
    v.active = true;
    return v;
  }

  public void rename(String name) {
    if (name != null && !name.isBlank()) {
      this.name = name;
    }
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
    if (!(o instanceof Variant other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
