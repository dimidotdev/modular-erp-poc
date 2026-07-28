package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.WorkflowTransition;
import dev.dimi.erp.ordemservico.internal.domain.WorkflowTransitionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTransitionRepository
    extends JpaRepository<WorkflowTransition, WorkflowTransitionId> {

  List<WorkflowTransition> findByIdFromStatusId(UUID fromStatusId);

  boolean existsByIdFromStatusIdAndIdToStatusId(UUID fromStatusId, UUID toStatusId);
}
