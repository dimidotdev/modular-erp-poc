package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.CatalogAddressType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogAddressTypeRepository extends JpaRepository<CatalogAddressType, UUID> {
  Optional<CatalogAddressType> findByCode(String code);

  boolean existsByCode(String code);
}
