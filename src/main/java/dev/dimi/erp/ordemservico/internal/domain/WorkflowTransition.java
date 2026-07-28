package dev.dimi.erp.ordemservico.internal.domain;

import dev.dimi.erp.ordemservico.WorkflowStatusId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Aresta do grafo de transições. {@code requiredPermission} opcional — quando presente, o
 * controller usa para gating adicional (ex: {@code READY → CANCELLED} exige {@code os.cancelar}).
 */
@Entity
@Table(name = "os_workflow_transition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id"})
public class WorkflowTransition {

  @EmbeddedId private WorkflowTransitionId id;

  @Column(name = "required_permission")
  private String requiredPermission;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public static WorkflowTransition allow(
      WorkflowStatusId fromStatusId, WorkflowStatusId toStatusId, String requiredPermission) {
    WorkflowTransition t = new WorkflowTransition();
    t.id = new WorkflowTransitionId(fromStatusId.value(), toStatusId.value());
    t.requiredPermission = requiredPermission;
    return t;
  }

  public Optional<String> requiredPermission() {
    return Optional.ofNullable(requiredPermission);
  }

  @PrePersist
  void onPersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WorkflowTransition other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
