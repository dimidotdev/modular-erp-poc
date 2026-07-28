package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.CatalogSegment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSegmentRepository extends JpaRepository<CatalogSegment, UUID> {
  Optional<CatalogSegment> findByCode(String code);

  boolean existsByCode(String code);
}
