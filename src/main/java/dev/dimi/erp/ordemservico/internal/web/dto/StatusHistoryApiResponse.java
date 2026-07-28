package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.internal.domain.StatusHistory;
import java.time.Instant;
import java.util.UUID;

public record StatusHistoryApiResponse(
    UUID id,
    UUID osId,
    UUID fromStatusId,
    UUID toStatusId,
    Instant changedAt,
    UUID changedBy,
    String notes) {

  public static StatusHistoryApiResponse from(StatusHistory h) {
    return new StatusHistoryApiResponse(
        h.getId(),
        h.getOsId(),
        h.getFromStatusId(),
        h.getToStatusId(),
        h.getChangedAt(),
        h.getChangedBy(),
        h.getNotes());
  }
}
