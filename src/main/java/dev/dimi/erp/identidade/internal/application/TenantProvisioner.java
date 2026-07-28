package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.TenantProvisionedEvent;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.domain.Role;
import dev.dimi.erp.identidade.internal.domain.RolePermissionAssignment;
import dev.dimi.erp.identidade.internal.domain.TenantEntity;
import dev.dimi.erp.identidade.internal.domain.UserAccount;
import dev.dimi.erp.identidade.internal.domain.UserRoleAssignment;
import dev.dimi.erp.identidade.internal.persistence.RolePermissionAssignmentRepository;
import dev.dimi.erp.identidade.internal.persistence.RoleRepository;
import dev.dimi.erp.identidade.internal.persistence.TenantRepository;
import dev.dimi.erp.identidade.internal.persistence.UserAccountRepository;
import dev.dimi.erp.identidade.internal.persistence.UserRoleAssignmentRepository;
import dev.dimi.erp.shared.modularization.TenantModuleProvisioner;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquestra a criação completa de um tenant: registro em {@code shared.tenant}, criação de schema
 * Postgres dedicado, aplicação das migrations de {@code db/migration/tenant/} no schema novo, seed
 * de system roles + permissions e criação do primeiro user OWNER com senha temporária.
 *
 * <p>Em caso de falha em qualquer etapa após o registro inicial, tenta limpar o estado parcial
 * (DROP SCHEMA + marca tenant FAILED) antes de relançar.
 *
 * <p>Operações cruzam schemas (shared para o catálogo, tenant_X para o conteúdo) usando o {@link
 * TenantContext} para guiar o Hibernate via {@link
 * dev.dimi.erp.shared.internal.multitenancy.CurrentTenantResolver}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioner {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final TenantRepository tenants;
  private final RoleRepository roles;
  private final RolePermissionAssignmentRepository rolePermissions;
  private final UserAccountRepository users;
  private final UserRoleAssignmentRepository userRoles;
  private final DataSource dataSource;
  private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
  private final TransactionTemplate tx;
  private final ApplicationEventPublisher events;
  private final TenantModuleProvisioner tenantModuleProvisioner;

  /**
   * Provisiona um tenant novo. Retorno inclui a {@code temporaryPassword} em claro do primeiro
   * OWNER (uma única vez; não persistida).
   */
  public ProvisionTenantResult provision(ProvisionTenantCommand cmd) {
    log.info("provisioning iniciado slug={}", cmd.slug());

    if (tenants.existsBySlug(cmd.slug())) {
      throw new TenantProvisioningException(cmd.slug(), "tenant slug já em uso: " + cmd.slug());
    }
    String schemaName = "tenant_" + cmd.slug().replace('-', '_');

    // 1) Registra tenant em PROVISIONING (TX em shared)
    TenantEntity tenant = saveProvisioningRecord(cmd, schemaName);

    String temporaryPassword = generateTemporaryPassword();
    String passwordHash = passwordEncoder.encode(temporaryPassword);

    UserId ownerUserId;
    try {
      // 2) DDL: cria schema
      createSchema(cmd.slug(), schemaName);

      // 3) Flyway: aplica db/migration/tenant/ no schema novo
      applyTenantMigrations(cmd.slug(), schemaName);

      // 4) Bootstrap: roles + first OWNER user dentro do schema novo
      ownerUserId = bootstrapTenantData(tenant, cmd, passwordHash);

      // 5) Marca ACTIVE e publica TenantProvisionedEvent dentro da MESMA TX —
      //    @ApplicationModuleListener é meta-anotado com
      //    @TransactionalEventListener(phase=AFTER_COMMIT, fallbackExecution=false),
      //    então o publish PRECISA ocorrer com TX ativa, senão o listener é
      //    silenciosamente descartado (era o bug que fazia o IT PLATFORM_ADMIN falhar).
      UserId ownerUserIdFinal = ownerUserId;
      tx.executeWithoutResult(
          status -> {
            tenants.findById(tenant.id().value()).ifPresent(TenantEntity::markActive);
            events.publishEvent(
                new TenantProvisionedEvent(
                    tenant.id(),
                    tenant.getSlug(),
                    tenant.getSchemaName(),
                    ownerUserIdFinal,
                    cmd.ownerUsername(),
                    Instant.now()));
          });

      log.info(
          "tenant provisioned slug={} schema={} ownerUser={}",
          cmd.slug(),
          schemaName,
          cmd.ownerUsername());

      return new ProvisionTenantResult(
          tenant.id(),
          tenant.getSlug(),
          tenant.getSchemaName(),
          ownerUserId,
          cmd.ownerUsername(),
          temporaryPassword);
    } catch (RuntimeException e) {
      log.error("provisioning failed slug={} -> rollback", cmd.slug(), e);
      markFailedSafely(tenant.id().value(), e.getMessage());
      dropSchemaSafely(schemaName);
      throw e instanceof TenantProvisioningException tpe
          ? tpe
          : new TenantProvisioningException(cmd.slug(), "falha provisionando tenant", e);
    }
  }

  // ------------------------------------------------------------------
  // Steps
  // ------------------------------------------------------------------

  private TenantEntity saveProvisioningRecord(ProvisionTenantCommand cmd, String schemaName) {
    return tx.execute(
        status ->
            tenants.save(
                TenantEntity.beginProvisioning(
                    cmd.slug(),
                    schemaName,
                    cmd.displayName(),
                    cmd.legalName(),
                    cmd.cnpj(),
                    cmd.plan())));
  }

  private void createSchema(String slug, String schemaName) {
    try (var conn = dataSource.getConnection();
        var st = conn.createStatement()) {
      st.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(schemaName));
    } catch (SQLException e) {
      throw new TenantProvisioningException(slug, "falha criando schema " + schemaName, e);
    }
  }

  private void applyTenantMigrations(String slug, String schemaName) {
    try {
      Flyway flyway =
          Flyway.configure()
              .dataSource(dataSource)
              .schemas(schemaName)
              .defaultSchema(schemaName)
              .locations("classpath:db/migration/tenant")
              .baselineOnMigrate(true)
              .createSchemas(false)
              .load();
      flyway.migrate();
    } catch (Exception e) {
      throw new TenantProvisioningException(
          slug, "falha aplicando migrations no schema " + schemaName, e);
    }
  }

  private UserId bootstrapTenantData(
      TenantEntity tenant, ProvisionTenantCommand cmd, String passwordHash) {
    Tenant ctx = new Tenant(tenant.id().value(), tenant.getSlug(), tenant.getSchemaName());
    TenantContext.set(ctx);
    try {
      return tx.execute(
          status -> {
            Map<String, Role> rolesByCode = seedSystemRoles();
            // Slice 11.5: popula tenant_module com todos os módulos catalogados. Core e
            // installed=true
            // (fiscal v1) ficam enabled=true; roadmap v1.1+
            // (frota/patrimônio/RH/POS/agro/imobiliária/
            // obras) ficam enabled=false. UI renderiza estes como "Instalar disponível em breve".
            tenantModuleProvisioner.seedAll();
            UserAccount owner =
                users.save(
                    UserAccount.register(
                        cmd.ownerUsername(),
                        cmd.ownerEmail(),
                        cmd.ownerFullName(),
                        passwordHash,
                        true));
            Role ownerRole = rolesByCode.get("OWNER");
            userRoles.save(UserRoleAssignment.assign(owner.id(), ownerRole.id(), null));
            return owner.id();
          });
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * Persiste as system roles + suas permissions no schema do tenant ativo (TenantContext já setado
   * pelo caller).
   */
  private Map<String, Role> seedSystemRoles() {
    Map<String, Role> result = new HashMap<>();
    for (SystemRoles.SystemRole sr : SystemRoles.ALL) {
      Role saved = roles.save(Role.create(sr.code(), sr.displayName(), sr.description(), true));
      for (String permissionCode : sr.permissions()) {
        rolePermissions.save(RolePermissionAssignment.grant(saved.id(), permissionCode));
      }
      result.put(sr.code(), saved);
    }
    return result;
  }

  // ------------------------------------------------------------------
  // Rollback helpers
  // ------------------------------------------------------------------

  private void markFailedSafely(java.util.UUID tenantId, String reason) {
    try {
      tx.executeWithoutResult(
          status -> tenants.findById(tenantId).ifPresent(t -> t.markFailed(reason)));
    } catch (Exception e) {
      log.warn("não foi possível marcar tenant como FAILED id={}", tenantId, e);
    }
  }

  private void dropSchemaSafely(String schemaName) {
    try (var conn = dataSource.getConnection();
        var st = conn.createStatement()) {
      st.execute("DROP SCHEMA IF EXISTS " + quoteIdentifier(schemaName) + " CASCADE");
    } catch (Exception e) {
      log.warn("não foi possível dropar schema {} após falha", schemaName, e);
    }
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /**
   * Segurança em profundidade: rejeita qualquer identifier fora do padrão {@code tenant_*}.
   * Validação já aconteceu antes (record Tenant, controller); aqui é último anteparo contra SQL
   * injection.
   */
  private static String quoteIdentifier(String schemaName) {
    if (schemaName == null || !schemaName.matches("^tenant_[a-z0-9_]+$")) {
      throw new IllegalArgumentException("schema name inválido: " + schemaName);
    }
    return "\"" + schemaName + "\"";
  }

  private static String generateTemporaryPassword() {
    byte[] bytes = new byte[12];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
