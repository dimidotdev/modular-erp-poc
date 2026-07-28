package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.CatalogRegion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogRegionRepository extends JpaRepository<CatalogRegion, UUID> {
  Optional<CatalogRegion> findByCode(String code);

  boolean existsByCode(String code);
}
