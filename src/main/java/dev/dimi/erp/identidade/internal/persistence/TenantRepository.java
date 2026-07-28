package dev.dimi.erp.identidade.internal.persistence;

import dev.dimi.erp.identidade.internal.domain.TenantEntity;
import dev.dimi.erp.identidade.internal.domain.TenantStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

  Optional<TenantEntity> findBySlug(String slug);

  Optional<TenantEntity> findBySchemaName(String schemaName);

  boolean existsBySlug(String slug);

  boolean existsByCnpj(String cnpj);

  List<TenantEntity> findAllByStatus(TenantStatus status);
}
