package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.StepUpPerformedEvent;
import dev.dimi.erp.identidade.internal.domain.TenantEntity;
import dev.dimi.erp.identidade.internal.domain.TenantStatus;
import dev.dimi.erp.identidade.internal.domain.UserAccount;
import dev.dimi.erp.identidade.internal.persistence.RolePermissionAssignmentRepository;
import dev.dimi.erp.identidade.internal.persistence.RoleRepository;
import dev.dimi.erp.identidade.internal.persistence.TenantRepository;
import dev.dimi.erp.identidade.internal.persistence.UserAccountRepository;
import dev.dimi.erp.identidade.internal.persistence.UserRoleAssignmentRepository;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Re-autentica um usuário já logado para emitir novo access token carregando a claim {@code sua}.
 *
 * <p>Fluxo:
 *
 * <ol>
 *   <li>Resolver tenant pelo slug do principal; exigir ACTIVE.
 *   <li>Setar {@link TenantContext}.
 *   <li>Carregar usuário por id; exigir {@code canLogin()}.
 *   <li>Comparar senha com Argon2id. Falha → {@link InvalidCredentialsException} (401 genérico).
 *   <li>Resolver roles + permissions atuais.
 *   <li>Emitir novo access JWT com {@code sua=now} (mantendo {@code sessionId} original).
 *   <li>Publicar {@link StepUpPerformedEvent} para auditoria.
 * </ol>
 *
 * <p>Refresh token NÃO é rotacionado — step-up é re-emissão de access apenas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StepUpService {

  private final TenantRepository tenants;
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final UserRoleAssignmentRepository userRoles;
  private final RolePermissionAssignmentRepository rolePermissions;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenIssuer jwtIssuer;
  private final LoginLockoutTracker lockoutTracker;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;

  public StepUpResult stepUp(StepUpCommand cmd) {
    lockoutTracker
        .checkLockout(cmd.tenantSlug(), cmd.username(), cmd.ip())
        .ifPresent(
            retryAfter -> {
              throw new AccountLockedException(retryAfter);
            });
    TenantEntity tenantEntity =
        tenants
            .findBySlug(cmd.tenantSlug())
            .orElseThrow(
                () -> {
                  recordFailure(cmd);
                  return new InvalidCredentialsException();
                });
    if (tenantEntity.getStatus() != TenantStatus.ACTIVE) {
      recordFailure(cmd);
      throw new InvalidCredentialsException();
    }
    Tenant tenant =
        new Tenant(tenantEntity.id().value(), tenantEntity.getSlug(), tenantEntity.getSchemaName());
    TenantContext.set(tenant);
    try {
      return tx.execute(status -> doStepUp(cmd, tenant));
    } finally {
      TenantContext.clear();
    }
  }

  private StepUpResult doStepUp(StepUpCommand cmd, Tenant tenant) {
    UserAccount user =
        users
            .findById(cmd.userId().value())
            .orElseThrow(
                () -> {
                  recordFailure(cmd);
                  return new InvalidCredentialsException();
                });

    if (!user.canLogin()) {
      recordFailure(cmd);
      throw new InvalidCredentialsException();
    }
    if (!passwordEncoder.matches(cmd.password(), user.getPasswordHash())) {
      recordFailure(cmd);
      log.warn(
          "step-up senha inválida tenant={} user={} ip={}",
          tenant.slug(),
          cmd.userId().value(),
          cmd.ip());
      throw new InvalidCredentialsException();
    }

    List<String> roleCodes = resolveRoleCodes(cmd.userId().value());
    List<String> permissionCodes =
        rolePermissions.findPermissionCodesByUserId(cmd.userId().value());

    Instant now = Instant.now();
    String accessToken =
        jwtIssuer.issueAccessToken(
            cmd.userId(),
            user.getUsername(),
            tenant,
            cmd.sessionId(),
            roleCodes,
            permissionCodes,
            List.of("pwd"),
            now);

    events.publishEvent(
        new StepUpPerformedEvent(
            cmd.userId(),
            cmd.tenantId(),
            tenant.slug(),
            cmd.sessionId(),
            cmd.ip(),
            cmd.userAgent(),
            now,
            now));
    lockoutTracker.recordSuccess(cmd.tenantSlug(), cmd.username());

    log.info(
        "step-up bem-sucedido tenant={} user={} session={}",
        tenant.slug(),
        cmd.userId().value(),
        cmd.sessionId().value());

    return new StepUpResult(accessToken, jwtIssuer.accessTokenTtl(), now);
  }

  private void recordFailure(StepUpCommand cmd) {
    lockoutTracker.recordFailure(cmd.tenantSlug(), cmd.username(), cmd.ip());
  }

  private List<String> resolveRoleCodes(UUID userId) {
    List<UUID> roleIds = userRoles.findRoleIdsByUserId(userId);
    return roleIds.stream()
        .map(roles::findById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(dev.dimi.erp.identidade.internal.domain.Role::getCode)
        .toList();
  }
}
