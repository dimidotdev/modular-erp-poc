package dev.dimi.erp.estoque.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
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
 * Local físico de estoque dentro de uma filial — prateleira, setor, gôndola. Cada filial tem ao
 * menos um local "padrão" (RN-EST-013) usado quando o movimento omite {@code locationId}.
 *
 * <p>Slice 4.3 foundation: estado + factories. CRUD endpoints e validação de default-único (mesmo
 * padrão de Variant/Packaging do produto) entram no slice 4.4.
 */
@Entity
@Table(name = "stock_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "branchId", "code", "isDefault", "active"})
public class StockLocation {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

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

  public StockLocationId id() {
    return new StockLocationId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public static StockLocation create(UUID branchId, String code, String name, boolean isDefault) {
    StockLocation l = new StockLocation();
    l.id = UuidCreator.getTimeOrderedEpoch();
    l.branchId = Objects.requireNonNull(branchId, "branchId");
    l.code = Objects.requireNonNull(code, "code");
    l.name = Objects.requireNonNull(name, "name");
    l.isDefault = isDefault;
    l.active = true;
    return l;
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
    if (!(o instanceof StockLocation other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
