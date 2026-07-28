package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.CatalogMeasurementUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogMeasurementUnitRepository
    extends JpaRepository<CatalogMeasurementUnit, UUID> {
  Optional<CatalogMeasurementUnit> findByCode(String code);

  boolean existsByCode(String code);
}
