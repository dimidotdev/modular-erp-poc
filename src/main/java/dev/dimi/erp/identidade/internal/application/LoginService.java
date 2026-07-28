package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.LoginFailedEvent;
import dev.dimi.erp.identidade.LoginSucceededEvent;
import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.internal.domain.LoginAttempt;
import dev.dimi.erp.identidade.internal.domain.RefreshToken;
import dev.dimi.erp.identidade.internal.domain.TenantEntity;
import dev.dimi.erp.identidade.internal.domain.TenantStatus;
import dev.dimi.erp.identidade.internal.domain.UserAccount;
import dev.dimi.erp.identidade.internal.persistence.LoginAttemptRepository;
import dev.dimi.erp.identidade.internal.persistence.RefreshTokenRepository;
import dev.dimi.erp.identidade.internal.persistence.RolePermissionAssignmentRepository;
import dev.dimi.erp.identidade.internal.persistence.RoleRepository;
import dev.dimi.erp.identidade.internal.persistence.TenantRepository;
import dev.dimi.erp.identidade.internal.persistence.UserAccountRepository;
import dev.dimi.erp.identidade.internal.persistence.UserRoleAssignmentRepository;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
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
 * Orquestra o fluxo de login: resolve tenant, autentica usuário, emite tokens, registra audit.
 *
 * <p>Fluxo:
 *
 * <ol>
 *   <li>Resolve {@code tenant} pelo slug em {@code shared.tenant}.
 *   <li>Valida estado do tenant (ACTIVE → ok; outros estados → bloqueio com motivo apropriado).
 *   <li>Seta {@link TenantContext} para que o Hibernate use o schema do tenant nas próximas
 *       queries.
 *   <li>Resolve usuário por username/email (case-insensitive).
 *   <li>Compara senha com Argon2id. Falha → registra {@code login_attempt} (failure) e lança {@link
 *       InvalidCredentialsException} (genérico).
 *   <li>Valida estado do usuário (ACTIVE → ok; SUSPENDED/DEACTIVATED → 403).
 *   <li>Resolve roles + permissions para incluir no JWT.
 *   <li>Emite access token JWT + refresh token (random + hash) e persiste o refresh.
 *   <li>Atualiza {@code user_account.last_login_*}.
 *   <li>Registra {@code login_attempt} (success) e publica {@link LoginSucceededEvent}.
 * </ol>
 *
 * <p>Trabalho transacional cruza schemas (shared para resolver tenant, tenant_X para tudo o mais),
 * então usamos {@link TransactionTemplate} ao invés de {@code @Transactional} em método único.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {

  private final TenantRepository tenants;
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final UserRoleAssignmentRepository userRoles;
  private final RolePermissionAssignmentRepository rolePermissions;
  private final RefreshTokenRepository refreshTokens;
  private final LoginAttemptRepository loginAttempts;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenIssuer jwtIssuer;
  private final RefreshTokenIssuer refreshIssuer;
  private final LoginLockoutTracker lockoutTracker;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;

  /** Tenta autenticar e retorna tokens. Falhas viram {@link DomainException} apropriadas. */
  public LoginResult authenticate(LoginCommand cmd) {
    Instant now = Instant.now();

    // 0) Lockout? — checa antes de qualquer trabalho. Bloqueio por (tenantSlug, username) OU IP.
    lockoutTracker
        .checkLockout(cmd.tenantSlug(), cmd.identifier(), cmd.ip())
        .ifPresent(
            retryAfter -> {
              log.warn(
                  "lockout ativo tenant={} identifier={} ip={} retryAfter={}s",
                  cmd.tenantSlug(),
                  cmd.identifier(),
                  cmd.ip(),
                  retryAfter.toSeconds());
              throw new AccountLockedException(retryAfter);
            });

    // 1) Resolver tenant pelo slug — em schema 'shared' (TenantContext ainda não setado)
    TenantEntity tenantEntity =
        tenants
            .findBySlug(cmd.tenantSlug())
            .orElseThrow(
                () -> {
                  recordTenantLevelFailure(cmd, null, "tenant-not-found", now);
                  return new TenantNotFoundException(cmd.tenantSlug());
                });

    // 2) Validar status do tenant
    if (tenantEntity.getStatus() != TenantStatus.ACTIVE) {
      recordTenantLevelFailure(
          cmd,
          new TenantId(tenantEntity.id().value()),
          "tenant-" + tenantEntity.getStatus().name().toLowerCase(),
          now);
      if (tenantEntity.getStatus() == TenantStatus.SUSPENDED
          || tenantEntity.getStatus() == TenantStatus.TERMINATED) {
        throw new TenantSuspendedException(cmd.tenantSlug());
      }
      // PROVISIONING ou FAILED — tratamos como credenciais inválidas (estado transiente/erro)
      throw new InvalidCredentialsException();
    }

    // 3) Setar TenantContext para próximas queries
    Tenant tenant =
        new Tenant(tenantEntity.id().value(), tenantEntity.getSlug(), tenantEntity.getSchemaName());
    TenantContext.set(tenant);

    try {
      return tx.execute(status -> doAuthenticate(cmd, tenant, now));
    } finally {
      TenantContext.clear();
    }
  }

  /**
   * Núcleo do fluxo executado dentro de transação no schema do tenant.
   *
   * <p>Suprime aviso de retorno {@code @Nullable} do TransactionTemplate: na prática este método
   * sempre retorna não-null ou lança exceção.
   */
  private LoginResult doAuthenticate(LoginCommand cmd, Tenant tenant, Instant now) {
    // 4) Buscar user por username ou email (case-insensitive)
    UserAccount user =
        users
            .findByUsernameOrEmail(cmd.identifier())
            .orElseThrow(
                () -> {
                  recordTenantScopedFailure(cmd, tenant, null, "user-not-found", now);
                  return new InvalidCredentialsException();
                });

    // 5) Comparar senha
    if (!passwordEncoder.matches(cmd.password(), user.getPasswordHash())) {
      recordTenantScopedFailure(cmd, tenant, user.id().value(), "invalid-password", now);
      throw new InvalidCredentialsException();
    }

    // 6) Validar estado da conta
    if (!user.canLogin()) {
      String reason = "user-" + user.getStatus().name().toLowerCase();
      recordTenantScopedFailure(cmd, tenant, user.id().value(), reason, now);
      throw new UserNotActiveException(user.getStatus().name());
    }

    // 7) Resolver roles + permissions
    List<String> roleCodes = resolveRoleCodes(user.id().value());
    List<String> permissionCodes = resolvePermissionCodes(user.id().value());

    // 8) Emitir tokens
    SessionId sessionId = SessionId.newId();
    List<String> amr = List.of("pwd");
    String accessToken =
        jwtIssuer.issueAccessToken(
            user.id(),
            user.getUsername(),
            tenant,
            sessionId,
            roleCodes,
            permissionCodes,
            amr,
            null);
    RefreshTokenIssuer.RawRefreshToken raw = refreshIssuer.issue(tenant.slug());
    RefreshToken refreshEntity =
        RefreshToken.issueNew(
            user.id(), sessionId, raw.hash(), raw.expiresAt(), cmd.userAgent(), cmd.ip());
    refreshTokens.save(refreshEntity);

    // 9) Atualizar last_login_* do usuário
    user.recordLogin(now, cmd.ip());

    // 10) Registrar login_attempt sucesso + publicar evento + resetar lockout do usuário
    loginAttempts.save(LoginAttempt.success(user.id().value(), cmd.identifier(), cmd.ip()));
    lockoutTracker.recordSuccess(tenant.slug(), cmd.identifier());
    events.publishEvent(
        new LoginSucceededEvent(
            user.id(),
            new TenantId(tenant.id()),
            tenant.slug(),
            sessionId,
            amr,
            cmd.ip(),
            cmd.userAgent(),
            now));

    log.info(
        "login sucedido tenant={} user={} session={}",
        tenant.slug(),
        user.id().value(),
        sessionId.value());

    return new LoginResult(
        user.id(),
        sessionId,
        accessToken,
        jwtIssuer.accessTokenTtl(),
        raw.raw(),
        refreshIssuer.refreshTokenTtl(),
        user.isMustChangePassword());
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

  private List<String> resolvePermissionCodes(UUID userId) {
    return rolePermissions.findPermissionCodesByUserId(userId);
  }

  /**
   * Falhas que aconteceram ANTES de setar TenantContext: tenant não existe/suspenso. Registramos em
   * audit log via evento; não temos schema de tenant para gravar {@code login_attempt}. {@code
   * tenantId} é {@code null} quando o slug não casou com nenhum tenant — listener de audit descarta
   * (sem PK para audit_event).
   */
  private void recordTenantLevelFailure(
      LoginCommand cmd, TenantId tenantId, String reason, Instant now) {
    lockoutTracker.recordFailure(cmd.tenantSlug(), cmd.identifier(), cmd.ip());
    events.publishEvent(
        new LoginFailedEvent(
            cmd.identifier(), tenantId, cmd.tenantSlug(), reason, cmd.ip(), cmd.userAgent(), now));
    log.warn("login falhou tenant={} reason={} ip={}", cmd.tenantSlug(), reason, cmd.ip());
  }

  /**
   * Falhas DENTRO do schema do tenant (TenantContext setado): registramos {@code login_attempt} e
   * publicamos evento. {@code userId} é {@code null} quando o identifier não casou com nenhum user.
   */
  private void recordTenantScopedFailure(
      LoginCommand cmd, Tenant tenant, UUID userId, String reason, Instant now) {
    loginAttempts.save(LoginAttempt.failure(userId, cmd.identifier(), cmd.ip(), reason));
    lockoutTracker.recordFailure(cmd.tenantSlug(), cmd.identifier(), cmd.ip());
    events.publishEvent(
        new LoginFailedEvent(
            cmd.identifier(),
            new TenantId(tenant.id()),
            tenant.slug(),
            reason,
            cmd.ip(),
            cmd.userAgent(),
            now));
    log.warn(
        "login falhou tenant={} identifier={} reason={} ip={}",
        cmd.tenantSlug(),
        cmd.identifier(),
        reason,
        cmd.ip());
  }
}
