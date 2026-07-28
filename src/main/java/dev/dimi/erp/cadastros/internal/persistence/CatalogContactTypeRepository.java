package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.CatalogContactType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogContactTypeRepository extends JpaRepository<CatalogContactType, UUID> {
  Optional<CatalogContactType> findByCode(String code);

  boolean existsByCode(String code);
}
