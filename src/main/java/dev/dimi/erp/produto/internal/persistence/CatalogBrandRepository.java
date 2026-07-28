package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.CatalogBrand;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogBrandRepository extends JpaRepository<CatalogBrand, UUID> {

  Optional<CatalogBrand> findByCode(String code);

  boolean existsByCode(String code);
}
