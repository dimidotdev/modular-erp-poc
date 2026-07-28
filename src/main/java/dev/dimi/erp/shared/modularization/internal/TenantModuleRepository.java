package dev.dimi.erp.shared.modularization.internal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleRepository extends JpaRepository<TenantModule, String> {
  // findAll() vem do parent
  Optional<TenantModule> findByCode(String code);

  List<TenantModule> findByEnabledTrue();
}
