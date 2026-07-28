package dev.dimi.erp.ordemservico.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.WorkflowStatusId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Log append-only de transições de status. <strong>APPEND-ONLY:</strong> sem {@code @PreUpdate}, e
 * todos os campos são {@code updatable=false}.
 */
@Entity
@Table(name = "service_order_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "osId", "fromStatusId", "toStatusId", "changedAt"})
public class StatusHistory {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "os_id", nullable = false, updatable = false)
  private UUID osId;

  @Column(name = "from_status_id", updatable = false)
  private UUID fromStatusId;

  @Column(name = "to_status_id", nullable = false, updatable = false)
  private UUID toStatusId;

  @Column(name = "changed_at", nullable = false, updatable = false)
  private Instant changedAt;

  @Column(name = "changed_by", nullable = false, updatable = false)
  private UUID changedBy;

  @Column(name = "notes", updatable = false)
  private String notes;

  public static StatusHistory record(
      ServiceOrderId osId,
      WorkflowStatusId fromStatusId,
      WorkflowStatusId toStatusId,
      UserId changedBy,
      String notes) {
    StatusHistory h = new StatusHistory();
    h.id = UuidCreator.getTimeOrderedEpoch();
    h.osId = Objects.requireNonNull(osId, "osId").value();
    h.fromStatusId = fromStatusId == null ? null : fromStatusId.value();
    h.toStatusId = Objects.requireNonNull(toStatusId, "toStatusId").value();
    h.changedBy = Objects.requireNonNull(changedBy, "changedBy").value();
    h.notes = notes;
    h.changedAt = Instant.now();
    return h;
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (changedAt == null) {
      changedAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StatusHistory other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
