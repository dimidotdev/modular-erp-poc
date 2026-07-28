package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.CatalogJobTitle;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogJobTitleRepository extends JpaRepository<CatalogJobTitle, UUID> {
  Optional<CatalogJobTitle> findByCode(String code);

  boolean existsByCode(String code);
}
