package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.TenantSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSettingRepository extends JpaRepository<TenantSetting, UUID> {

  Optional<TenantSetting> findBySingletonKey(boolean singletonKey);

  default Optional<TenantSetting> findSingleton() {
    return findBySingletonKey(true);
  }
}
