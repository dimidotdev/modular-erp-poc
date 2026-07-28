package dev.dimi.erp.shared.modularization.internal.adapter;

import dev.dimi.erp.shared.modularization.TenantModuleProvisioner;
import dev.dimi.erp.shared.modularization.internal.ModuleAccessService;
import org.springframework.stereotype.Component;

/**
 * Adapter da port {@link TenantModuleProvisioner} delegando ao {@link ModuleAccessService} interno.
 */
@Component
class TenantModuleProvisionerAdapter implements TenantModuleProvisioner {

  private final ModuleAccessService access;

  TenantModuleProvisionerAdapter(ModuleAccessService access) {
    this.access = access;
  }

  @Override
  public int seedAll() {
    return access.seedCatalog();
  }
}
