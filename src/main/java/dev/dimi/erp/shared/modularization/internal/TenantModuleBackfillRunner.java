package dev.dimi.erp.shared.modularization.internal;

import dev.dimi.erp.identidade.TenantQueryPort;
import dev.dimi.erp.shared.modularization.TenantModuleProvisioner;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Boot-time backfill: itera tenants {@code ACTIVE} e garante que cada um tem rows {@code
 * tenant_module} para todos os módulos catalogados. Roda uma vez ao subir o app e é idempotente —
 * tenants já backfilled custam apenas uma query {@code SELECT}.
 *
 * <p>Necessário porque a migration {@code V25__create_tenant_module.sql} apenas cria a tabela
 * vazia; o seed efetivo é responsabilidade de Java ({@link TenantModuleProvisioner#seedAll()}).
 * Tenants criados <em>após</em> slice 11.5 recebem seed inline via {@code TenantProvisioner};
 * tenants <em>pré-existentes</em> dependem deste runner.
 *
 * <p>Desativável via {@code erp-poc.modularization.backfill.enabled=false} — útil em testes/CI que
 * sobem o contexto sem tenant catálogo populado.
 */
@Component
@ConditionalOnProperty(
    name = "erp-poc.modularization.backfill.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class TenantModuleBackfillRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(TenantModuleBackfillRunner.class);

  private final TenantQueryPort tenants;
  private final TenantModuleProvisioner provisioner;
  private final TransactionTemplate tx;

  public TenantModuleBackfillRunner(
      TenantQueryPort tenants,
      TenantModuleProvisioner provisioner,
      PlatformTransactionManager txm) {
    this.tenants = tenants;
    this.provisioner = provisioner;
    this.tx = new TransactionTemplate(txm);
  }

  @Override
  public void run(ApplicationArguments args) {
    int totalAdded = 0;
    int tenantsScanned = 0;
    for (Tenant tenant : tenants.findAllActive()) {
      tenantsScanned++;
      try {
        TenantContext.set(tenant);
        Integer addedHere = tx.execute(status -> provisioner.seedAll());
        if (addedHere != null) {
          totalAdded += addedHere;
        }
      } catch (RuntimeException e) {
        log.warn("tenant_module backfill failed for tenant {}", tenant.slug(), e);
      } finally {
        TenantContext.clear();
      }
    }
    log.info(
        "tenant_module backfill complete; tenantsScanned={} rowsAdded={}",
        tenantsScanned,
        totalAdded);
  }
}
