package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.OsLabor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OsLaborRepository extends JpaRepository<OsLabor, UUID> {

  List<OsLabor> findByOsIdOrderByStartedAtAsc(UUID osId);
}
