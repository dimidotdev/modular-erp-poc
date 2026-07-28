package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.OsService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OsServiceRepository extends JpaRepository<OsService, UUID> {

  List<OsService> findByOsIdOrderByLineNumberAsc(UUID osId);

  Optional<OsService> findByOsIdAndLineNumber(UUID osId, int lineNumber);

  @Query("select coalesce(max(s.lineNumber), 0) from OsService s where s.osId = :osId")
  int maxLineByOs(UUID osId);
}
