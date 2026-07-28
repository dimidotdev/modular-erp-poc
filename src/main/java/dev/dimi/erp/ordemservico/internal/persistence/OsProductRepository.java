package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OsProductRepository extends JpaRepository<OsProduct, UUID> {

  List<OsProduct> findByOsIdOrderByLineNumberAsc(UUID osId);

  Optional<OsProduct> findByOsIdAndLineNumber(UUID osId, int lineNumber);

  @Query("select coalesce(max(p.lineNumber), 0) from OsProduct p where p.osId = :osId")
  int maxLineByOs(UUID osId);
}
