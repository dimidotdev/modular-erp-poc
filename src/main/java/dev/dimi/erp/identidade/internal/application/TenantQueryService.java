package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.TenantQueryPort;
import dev.dimi.erp.identidade.internal.domain.TenantStatus;
import dev.dimi.erp.identidade.internal.persistence.TenantRepository;
import dev.dimi.erp.shared.multitenancy.Tenant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapter interno que expõe {@link TenantQueryPort}. */
@Service
@RequiredArgsConstructor
class TenantQueryService implements TenantQueryPort {

  private final TenantRepository tenants;

  @Override
  @Transactional(readOnly = true)
  public List<Tenant> findAllActive() {
    return tenants.findAllByStatus(TenantStatus.ACTIVE).stream()
        .map(t -> new Tenant(t.id().value(), t.getSlug(), t.getSchemaName()))
        .toList();
  }
}
