package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.internal.domain.WorkflowTransition;
import java.util.UUID;

public record WorkflowTransitionApiResponse(
    UUID fromStatusId, UUID toStatusId, String requiredPermission) {

  public static WorkflowTransitionApiResponse from(WorkflowTransition t) {
    return new WorkflowTransitionApiResponse(
        t.getId().getFromStatusId(),
        t.getId().getToStatusId(),
        t.requiredPermission().orElse(null));
  }
}
