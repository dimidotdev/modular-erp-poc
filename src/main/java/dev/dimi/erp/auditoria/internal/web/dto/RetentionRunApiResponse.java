package dev.dimi.erp.auditoria.internal.web.dto;

import dev.dimi.erp.auditoria.internal.domain.RetentionRun;
import dev.dimi.erp.auditoria.internal.domain.RunStatus;
import java.time.Instant;
import java.util.UUID;

/** Representação REST de {@code audit.retention_run} — trilha histórica do job de retenção. */
public record RetentionRunApiResponse(
    UUID id,
    Instant startedAt,
    Instant finishedAt,
    RunStatus status,
    long purgedCount,
    long anonymizedCount,
    int partitionsDropped,
    int partitionsCreated,
    String errorDetail) {

  public static RetentionRunApiResponse from(RetentionRun r) {
    return new RetentionRunApiResponse(
        r.getId(),
        r.getStartedAt(),
        r.getFinishedAt(),
        r.getStatus(),
        r.getPurgedCount(),
        r.getAnonymizedCount(),
        r.getPartitionsDropped(),
        r.getPartitionsCreated(),
        r.getErrorDetail());
  }
}
