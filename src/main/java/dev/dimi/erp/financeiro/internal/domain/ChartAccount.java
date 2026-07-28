package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.ChartAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Conta do plano de contas. Hierárquica via {@code parentId}. Contas sintéticas ({@code
 * isSynthetic=true}) só agregam — não recebem lançamento direto (RN-FIN-001).
 */
@Entity
@Table(name = "chart_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "name", "type"})
public class ChartAccount {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private ChartAccountType type;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(name = "is_synthetic", nullable = false)
  private boolean isSynthetic;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ChartAccountId id() {
    return new ChartAccountId(id);
  }

  public ChartAccountId parentIdTyped() {
    return parentId == null ? null : new ChartAccountId(parentId);
  }

  public static ChartAccount create(
      String code, String name, ChartAccountType type, UUID parentId, boolean isSynthetic) {
    ChartAccount a = new ChartAccount();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.code = Objects.requireNonNull(code, "code");
    a.name = Objects.requireNonNull(name, "name");
    a.type = Objects.requireNonNull(type, "type");
    a.parentId = parentId;
    a.isSynthetic = isSynthetic;
    a.isActive = true;
    a.sortOrder = 0;
    return a;
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
    if (!(o instanceof ChartAccount other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
