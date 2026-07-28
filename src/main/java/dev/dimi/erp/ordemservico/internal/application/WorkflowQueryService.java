package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.ordemservico.WorkflowStatusId;
import dev.dimi.erp.ordemservico.internal.domain.WorkflowStatus;
import dev.dimi.erp.ordemservico.internal.domain.WorkflowTransition;
import dev.dimi.erp.ordemservico.internal.persistence.WorkflowStatusRepository;
import dev.dimi.erp.ordemservico.internal.persistence.WorkflowTransitionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries sobre o workflow (status + transições). CRUD de status/transições custom fica para 9.2 —
 * 9.1 só lê o catálogo já seedado.
 */
@Service
public class WorkflowQueryService {

  private final WorkflowStatusRepository statuses;
  private final WorkflowTransitionRepository transitions;

  public WorkflowQueryService(
      WorkflowStatusRepository statuses, WorkflowTransitionRepository transitions) {
    this.statuses = statuses;
    this.transitions = transitions;
  }

  @Transactional(readOnly = true)
  public List<WorkflowStatus> listStatuses() {
    return statuses.findAllByOrderBySortOrderAscCodeAsc();
  }

  @Transactional(readOnly = true)
  public List<WorkflowTransition> transitionsFrom(WorkflowStatusId fromId) {
    return transitions.findByIdFromStatusId(fromId.value());
  }
}
