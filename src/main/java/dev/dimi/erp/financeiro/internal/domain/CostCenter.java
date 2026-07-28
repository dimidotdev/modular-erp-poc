package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.CostCenterId;
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
 * Centro de custo hierárquico (Filial/Departamento/Projeto). Soft-delete via {@code isActive}
 * (RN-FIN-003).
 */
@Entity
@Table(name = "cost_center")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "name"})
public class CostCenter {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public CostCenterId id() {
    return new CostCenterId(id);
  }

  public CostCenterId parentIdTyped() {
    return parentId == null ? null : new CostCenterId(parentId);
  }

  public static CostCenter create(String code, String name, UUID parentId) {
    CostCenter c = new CostCenter();
    c.id = UuidCreator.getTimeOrderedEpoch();
    c.code = Objects.requireNonNull(code, "code");
    c.name = Objects.requireNonNull(name, "name");
    c.parentId = parentId;
    c.isActive = true;
    c.sortOrder = 0;
    return c;
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
    if (!(o instanceof CostCenter other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
