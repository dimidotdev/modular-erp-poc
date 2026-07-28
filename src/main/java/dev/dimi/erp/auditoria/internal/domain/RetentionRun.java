package dev.dimi.erp.auditoria.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Trilha histórica de execuções do job de retenção. Espelha {@code audit.retention_run} (V9
 * global).
 *
 * <p>Cada execução do job grava uma linha aqui via {@link #start()}, atualiza para SUCCESS via
 * {@link #markSuccess} (com contagens) ou FAILED via {@link #markFailed} (com mensagem). Permite
 * dashboard administrativo e detecção de jobs travados (RUNNING há mais que o timeout do ShedLock).
 */
@Entity
@Table(name = "retention_run", schema = "audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "status", "startedAt", "finishedAt"})
public class RetentionRun {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private RunStatus status;

  @Column(name = "purged_count", nullable = false)
  private long purgedCount;

  @Column(name = "anonymized_count", nullable = false)
  private long anonymizedCount;

  @Column(name = "partitions_dropped", nullable = false)
  private int partitionsDropped;

  @Column(name = "partitions_created", nullable = false)
  private int partitionsCreated;

  @Column(name = "error_detail")
  private String errorDetail;

  /** Cria uma nova execução marcada como {@link RunStatus#RUNNING} com {@code startedAt = now}. */
  public static RetentionRun start() {
    RetentionRun r = new RetentionRun();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.status = RunStatus.RUNNING;
    r.startedAt = Instant.now();
    r.purgedCount = 0L;
    r.anonymizedCount = 0L;
    r.partitionsDropped = 0;
    r.partitionsCreated = 0;
    return r;
  }

  public void markSuccess(long purged, long anonymized, int partsDropped, int partsCreated) {
    this.purgedCount = purged;
    this.anonymizedCount = anonymized;
    this.partitionsDropped = partsDropped;
    this.partitionsCreated = partsCreated;
    this.status = RunStatus.SUCCESS;
    this.finishedAt = Instant.now();
  }

  public void markFailed(String error) {
    this.status = RunStatus.FAILED;
    this.errorDetail = error;
    this.finishedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RetentionRun other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
