package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.produto.BrandId;
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
 * Catálogo de marcas do tenant. Diferente dos catálogos de {@code cadastros}, não há marcas
 * seedadas globalmente — cada tenant cadastra as próprias —, por isso não há flag {@code
 * is_system}.
 */
@Entity
@Table(name = "catalog_brand")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "name", "active"})
public class CatalogBrand {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public BrandId id() {
    return new BrandId(id);
  }

  /** Cria nova marca ativa. */
  public static CatalogBrand create(String code, String name) {
    CatalogBrand b = new CatalogBrand();
    b.id = UuidCreator.getTimeOrderedEpoch();
    b.code = Objects.requireNonNull(code, "code");
    b.name = Objects.requireNonNull(name, "name");
    b.active = true;
    return b;
  }

  public void rename(String name) {
    if (name != null && !name.isBlank()) {
      this.name = name;
    }
  }

  public void deactivate() {
    this.active = false;
  }

  public void reactivate() {
    this.active = true;
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
    if (!(o instanceof CatalogBrand other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
