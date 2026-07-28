package dev.dimi.erp.shared.internal.multitenancy;

import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Resolve o identifier do tenant corrente lendo do {@link TenantContext}.
 *
 * <p>Quando não há tenant no contexto (ex: requisições para {@code /platform/*} ou {@code /auth/*}
 * antes do filter setar) retorna {@code "shared"} como fallback — operações que precisam ler
 * tabelas de tenant <em>devem</em> setar o context explicitamente; operações em {@code shared} ou
 * que apenas consultam metadados funcionam com o fallback.
 */
class CurrentTenantResolver implements CurrentTenantIdentifierResolver<String> {

  private static final String DEFAULT = "shared";

  @Override
  public String resolveCurrentTenantIdentifier() {
    return TenantContext.current().map(Tenant::schemaName).orElse(DEFAULT);
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    // O tenant não muda no meio de uma sessão Hibernate — set é feito no início
    // da requisição e mantido até o final.
    return false;
  }
}
