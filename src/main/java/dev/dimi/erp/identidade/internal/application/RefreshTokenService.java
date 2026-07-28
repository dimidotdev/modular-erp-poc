package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.SessionRevokedAllEvent;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.domain.RefreshToken;
import dev.dimi.erp.identidade.internal.domain.TenantEntity;
import dev.dimi.erp.identidade.internal.domain.TenantStatus;
import dev.dimi.erp.identidade.internal.domain.UserAccount;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquestra rotação de refresh tokens e logout.
 *
 * <p>Detalhes em {@code docs/architecture/02-seguranca.md} e {@code
 * docs/domain/10-identidade-acesso.md}.
 *
 * <h2>Rotation flow</h2>
 *
 * <ol>
 *   <li>Extrair {@code tenantSlug} embutido no raw token; resolver tenant.
 *   <li>Setar {@link TenantContext}.
 *   <li>Hash do raw → buscar {@link RefreshToken}.
 *   <li>Validações em ordem (cada falha → 401 genérico):
 *       <ul>
 *         <li>Token existe?
 *         <li>Token já foi usado? Se sim, REUSE DETECTED → revoga chain.
 *         <li>Token revogado?
 *         <li>Token expirado (absolute TTL)?
 *         <li>Token ocioso (idle TTL desde issuedAt)?
 *         <li>Usuário existe e está ACTIVE?
 *       </ul>
 *   <li>Marcar token usado.
 *   <li>Resolver roles + permissions.
 *   <li>Emitir novo access JWT + novo refresh token na mesma chain.
 *   <li>Persistir o novo refresh.
 * </ol>
 *
 * <h2>Logout flow</h2>
 *
 * Resolve tenant via slug embutido, busca refresh por hash, revoga ou a sessão (logout) ou todas do
 * usuário (logout-all). Idempotente: token desconhecido é no-op.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

  private final TenantRepository tenants;
  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final UserRoleAssignmentRepository userRoles;
  private final RolePermissionAssignmentRepository rolePermissions;
  private final RefreshTokenRepository refreshTokens;
  private final JwtTokenIssuer jwtIssuer;
  private final RefreshTokenIssuer refreshIssuer;
  private final ApplicationEventPublisher events;
  private final RefreshTokenChainRevoker chainRevoker;
  private final TransactionTemplate tx;

  /** Rotaciona o refresh token: marca o atual como usado e emite novo par access+refresh. */
  public LoginResult rotate(RefreshCommand cmd) {
    Tenant tenant = resolveTenantOrThrow(cmd.rawRefreshToken());
    TenantContext.set(tenant);
    try {
      return tx.execute(status -> doRotate(cmd, tenant));
    } finally {
      TenantContext.clear();
    }
  }

  /** Revoga a sessão associada ao refresh token (ou todas do usuário se {@code allSessions}). */
  public void logout(LogoutCommand cmd, String triggeringIp) {
    Tenant tenant;
    try {
      tenant = resolveTenantOrThrow(cmd.rawRefreshToken());
    } catch (InvalidRefreshTokenException e) {
      // Logout idempotente: token com slug inválido = no-op, não 401.
      log.debug("logout com token de slug inválido — no-op");
      return;
    }
    TenantContext.set(tenant);
    try {
      tx.executeWithoutResult(status -> doLogout(cmd, tenant, triggeringIp));
    } finally {
      TenantContext.clear();
    }
  }

  // ------------------------------------------------------------------
  // Steps
  // ------------------------------------------------------------------

  private Tenant resolveTenantOrThrow(String rawToken) {
    String slug =
        RefreshTokenIssuer.extractTenantSlug(rawToken)
            .orElseThrow(InvalidRefreshTokenException::new);
    TenantEntity entity = tenants.findBySlug(slug).orElseThrow(InvalidRefreshTokenException::new);
    if (entity.getStatus() != TenantStatus.ACTIVE) {
      throw new InvalidRefreshTokenException();
    }
    return new Tenant(entity.id().value(), entity.getSlug(), entity.getSchemaName());
  }

  private LoginResult doRotate(RefreshCommand cmd, Tenant tenant) {
    String hash = RefreshTokenIssuer.sha256(cmd.rawRefreshToken());

    RefreshToken token =
        refreshTokens.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);

    // REUSE DETECTION: token já consumido → revoga toda a chain e alerta.
    // Revogação acontece em transação REQUIRES_NEW (RefreshTokenChainRevoker) para que o
    // RefreshTokenReuseException abaixo não desfaça o UPDATE via rollback da outer tx.
    if (token.wasUsed()) {
      int revoked =
          chainRevoker.revokeChainOnReuse(
              token.getChainId(),
              token.getUserId(),
              new TenantId(tenant.id()),
              tenant.slug(),
              cmd.ip(),
              Instant.now());
      log.error(
          "REFRESH REUSE DETECTED tenant={} user={} session={} chain={} revoked={}",
          tenant.slug(),
          token.getUserId(),
          token.getSessionId(),
          token.getChainId(),
          revoked);
      throw new RefreshTokenReuseException();
    }

    if (token.isRevoked()) {
      throw new InvalidRefreshTokenException();
    }

    Instant now = Instant.now();
    if (!token.isActive(now)) {
      throw new InvalidRefreshTokenException();
    }

    // Idle TTL: se nunca foi rotacionado e foi emitido há mais que idleTtl, expira.
    // (Como rotate marca o anterior como used, o "vivo" é sempre o último emitido — então
    // comparamos issuedAt do atual com now.)
    if (Instant.now().isAfter(token.getIssuedAt().plus(refreshIssuer.refreshIdleTtl()))) {
      throw new InvalidRefreshTokenException();
    }

    UserAccount user =
        users.findById(token.getUserId()).orElseThrow(InvalidRefreshTokenException::new);
    if (!user.canLogin()) {
      throw new InvalidRefreshTokenException();
    }

    // Consome o atual
    token.markUsed();

    // Resolve roles + perms
    List<String> roleCodes = resolveRoleCodes(user.id().value());
    List<String> permissionCodes = rolePermissions.findPermissionCodesByUserId(user.id().value());

    // Emite novo par
    SessionId sessionId = new SessionId(token.getSessionId());
    // Mantém amr da sessão original; quando JWT filter chegar (Etapa 7), lemos do refresh-issued
    // JWT.
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
    RefreshToken next =
        RefreshToken.rotateFrom(token, raw.hash(), raw.expiresAt(), cmd.userAgent(), cmd.ip());
    refreshTokens.save(next);

    log.info(
        "refresh rotacionado tenant={} user={} session={}",
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

  private void doLogout(LogoutCommand cmd, Tenant tenant, String triggeringIp) {
    String hash = RefreshTokenIssuer.sha256(cmd.rawRefreshToken());
    Optional<RefreshToken> maybe = refreshTokens.findByTokenHash(hash);
    if (maybe.isEmpty()) {
      log.debug("logout com hash desconhecido — no-op (idempotente)");
      return;
    }
    RefreshToken token = maybe.get();
    int revoked;
    String reason;
    if (cmd.allSessions()) {
      reason = "LOGOUT_ALL";
      revoked = refreshTokens.revokeAllByUserId(token.getUserId(), reason);
      events.publishEvent(
          new SessionRevokedAllEvent(
              new UserId(token.getUserId()),
              new TenantId(tenant.id()),
              tenant.slug(),
              reason,
              revoked,
              triggeringIp,
              Instant.now()));
    } else {
      reason = "LOGOUT";
      revoked = refreshTokens.revokeAllBySessionId(token.getSessionId(), reason);
    }
    log.info(
        "logout tenant={} user={} session={} all={} revoked={}",
        tenant.slug(),
        token.getUserId(),
        token.getSessionId(),
        cmd.allSessions(),
        revoked);
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
