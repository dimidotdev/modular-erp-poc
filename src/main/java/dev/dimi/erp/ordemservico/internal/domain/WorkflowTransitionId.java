package dev.dimi.erp.ordemservico.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de {@link WorkflowTransition}: {@code (from_status_id, to_status_id)}. */
@Embeddable
public class WorkflowTransitionId implements Serializable {

  @Column(name = "from_status_id", nullable = false)
  private UUID fromStatusId;

  @Column(name = "to_status_id", nullable = false)
  private UUID toStatusId;

  protected WorkflowTransitionId() {
    // jpa
  }

  public WorkflowTransitionId(UUID fromStatusId, UUID toStatusId) {
    this.fromStatusId = Objects.requireNonNull(fromStatusId, "fromStatusId");
    this.toStatusId = Objects.requireNonNull(toStatusId, "toStatusId");
  }

  public UUID getFromStatusId() {
    return fromStatusId;
  }

  public UUID getToStatusId() {
    return toStatusId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WorkflowTransitionId other)) return false;
    return Objects.equals(fromStatusId, other.fromStatusId)
        && Objects.equals(toStatusId, other.toStatusId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fromStatusId, toStatusId);
  }
}
