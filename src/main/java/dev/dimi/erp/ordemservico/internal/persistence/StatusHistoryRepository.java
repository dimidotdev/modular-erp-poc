package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.StatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, UUID> {

  List<StatusHistory> findByOsIdOrderByChangedAtAsc(UUID osId);
}
