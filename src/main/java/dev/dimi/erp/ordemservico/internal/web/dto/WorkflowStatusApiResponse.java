package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.internal.domain.WorkflowStatus;
import java.util.UUID;

public record WorkflowStatusApiResponse(
    UUID id,
    String code,
    String displayName,
    boolean isInitial,
    boolean isTerminal,
    boolean isBillable,
    boolean isSystem,
    int sortOrder) {

  public static WorkflowStatusApiResponse from(WorkflowStatus s) {
    return new WorkflowStatusApiResponse(
        s.getId(),
        s.getCode(),
        s.getDisplayName(),
        s.isInitial(),
        s.isTerminal(),
        s.isBillable(),
        s.isSystem(),
        s.getSortOrder());
  }
}
