package dev.dimi.erp.ordemservico.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.ordemservico.WorkflowStatusId;
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
 * Status do workflow de OS. Tenant-scoped: 7 status default ({@code is_system=true}) já vêm no seed
 * V20 — tenant pode adicionar custom (ex: {@code AWAITING_APPROVAL}) mas não pode alterar/remover
 * os system.
 */
@Entity
@Table(name = "os_workflow_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "isSystem"})
public class WorkflowStatus {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, updatable = false)
  private String code;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "is_initial", nullable = false)
  private boolean isInitial;

  @Column(name = "is_terminal", nullable = false)
  private boolean isTerminal;

  @Column(name = "is_billable", nullable = false)
  private boolean isBillable;

  @Column(name = "is_system", nullable = false, updatable = false)
  private boolean isSystem;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public WorkflowStatusId typedId() {
    return new WorkflowStatusId(id);
  }

  public static WorkflowStatus newCustom(
      String code,
      String displayName,
      boolean isInitial,
      boolean isTerminal,
      boolean isBillable,
      int sortOrder) {
    WorkflowStatus s = new WorkflowStatus();
    s.id = UuidCreator.getTimeOrderedEpoch();
    s.code = Objects.requireNonNull(code, "code");
    s.displayName = Objects.requireNonNull(displayName, "displayName");
    s.isInitial = isInitial;
    s.isTerminal = isTerminal;
    s.isBillable = isBillable;
    s.isSystem = false;
    s.sortOrder = sortOrder;
    return s;
  }

  public void update(String displayName, boolean isBillable, int sortOrder) {
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.isBillable = isBillable;
    this.sortOrder = sortOrder;
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
    if (!(o instanceof WorkflowStatus other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
