package dev.dimi.erp.ordemservico;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

/** ID tipado para {@code os_workflow_status}. */
public record WorkflowStatusId(@JsonValue UUID value) {

  public WorkflowStatusId {
    Objects.requireNonNull(value, "workflow status id é obrigatório");
  }

  public static WorkflowStatusId of(String raw) {
    return new WorkflowStatusId(UUID.fromString(raw));
  }
}
