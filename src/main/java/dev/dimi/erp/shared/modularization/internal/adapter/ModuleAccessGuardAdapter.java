package dev.dimi.erp.shared.modularization.internal.adapter;

import dev.dimi.erp.shared.modularization.ModuleAccessDeniedException;
import dev.dimi.erp.shared.modularization.ModuleAccessGuard;
import dev.dimi.erp.shared.modularization.internal.ModuleAccessService;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import org.springframework.stereotype.Component;

/** Adapter da port {@link ModuleAccessGuard} delegando ao {@link ModuleAccessService} interno. */
@Component
class ModuleAccessGuardAdapter implements ModuleAccessGuard {

  private final ModuleAccessService access;

  ModuleAccessGuardAdapter(ModuleAccessService access) {
    this.access = access;
  }

  @Override
  public void requireEnabled(String moduleCode) {
    String tenantKey = TenantContext.currentOrThrow().slug();
    if (!access.isEnabled(tenantKey, moduleCode)) {
      throw new ModuleAccessDeniedException(moduleCode);
    }
  }
}
