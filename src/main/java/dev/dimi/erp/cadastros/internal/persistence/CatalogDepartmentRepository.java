package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.CatalogDepartment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogDepartmentRepository extends JpaRepository<CatalogDepartment, UUID> {
  Optional<CatalogDepartment> findByCode(String code);

  boolean existsByCode(String code);
}
