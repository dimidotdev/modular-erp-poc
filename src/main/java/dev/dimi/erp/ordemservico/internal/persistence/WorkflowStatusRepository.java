package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.WorkflowStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, UUID> {

  Optional<WorkflowStatus> findByCode(String code);

  List<WorkflowStatus> findAllByOrderBySortOrderAscCodeAsc();

  Optional<WorkflowStatus> findFirstByIsInitialTrue();
}
