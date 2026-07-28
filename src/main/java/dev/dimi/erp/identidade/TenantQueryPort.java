package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.Tenant;
import java.util.List;

/**
 * Porta de consulta pública de tenants para jobs cross-tenant (ex: detecção de certificados A1
 * próximos da expiração — RN-CAD-009). Cadastros e outros módulos com schedulers iteram aqui e usam
 * {@link dev.dimi.erp.shared.multitenancy.TenantContext#set} para entrar em cada schema.
 *
 * <p>Implementação interna em {@code identidade.internal.application.TenantQueryService}.
 */
public interface TenantQueryPort {

  /** Tenants em estado {@code ACTIVE} (exclui {@code SUSPENDED}, {@code TERMINATED}, etc). */
  List<Tenant> findAllActive();
}
