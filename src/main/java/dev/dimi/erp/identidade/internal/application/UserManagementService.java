package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.UserCreatedEvent;
import dev.dimi.erp.identidade.UserDeactivatedEvent;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.UserReactivatedEvent;
import dev.dimi.erp.identidade.internal.domain.Role;
import dev.dimi.erp.identidade.internal.domain.UserAccount;
import dev.dimi.erp.identidade.internal.domain.UserRoleAssignment;
import dev.dimi.erp.identidade.internal.domain.UserStatus;
import dev.dimi.erp.identidade.internal.persistence.RolePermissionAssignmentRepository;
import dev.dimi.erp.identidade.internal.persistence.RoleRepository;
import dev.dimi.erp.identidade.internal.persistence.UserAccountRepository;
import dev.dimi.erp.identidade.internal.persistence.UserRoleAssignmentRepository;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações administrativas sobre usuários dentro do tenant ativo.
 *
 * <p>Todas as queries deste serviço rodam no schema do tenant atual — o {@code
 * JwtAuthenticationFilter} já populou o {@code TenantContext} antes do controller. Não confunda com
 * {@link TenantProvisioner}, que cria o primeiro OWNER durante o provisioning (sem usuário
 * autenticado, contexto manual).
 *
 * <p>Operações de v1: criar, listar, buscar, desativar, reativar. Update de dados pessoais e troca
 * de roles ficam para slice específico — exigem step-up auth e audit fino, fora do escopo desta
 * etapa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

  private static final String OWNER_ROLE = "OWNER";
  private static final String ALTER_PERMISSIONS = "usuario.permissoes.alterar";

  private final UserAccountRepository users;
  private final RoleRepository roles;
  private final RolePermissionAssignmentRepository rolePermissions;
  private final UserRoleAssignmentRepository userRoles;
  private final PasswordPolicyValidator passwordPolicy;
  private final PasswordEncoder passwordEncoder;
  private final ApplicationEventPublisher events;

  @Transactional
  public UserSummary create(CreateUserCommand cmd) {
    passwordPolicy.validate(cmd.password(), cmd.username(), cmd.email(), cmd.fullName());

    if (users.existsByUsernameIgnoreCase(cmd.username())) {
      throw new UserAlreadyExistsException("username", cmd.username());
    }
    if (users.existsByEmailIgnoreCase(cmd.email())) {
      throw new UserAlreadyExistsException("email", cmd.email());
    }

    List<Role> resolvedRoles = resolveRolesByCode(cmd.roleCodes());
    validateRoleAssignments(resolvedRoles, cmd.createdBy());

    UserAccount saved =
        users.save(
            UserAccount.register(
                cmd.username(),
                cmd.email(),
                cmd.fullName(),
                passwordEncoder.encode(cmd.password()),
                cmd.mustChangePassword()));

    for (Role role : resolvedRoles) {
      userRoles.save(UserRoleAssignment.assign(saved.id(), role.id(), cmd.createdBy()));
    }

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new UserCreatedEvent(
            saved.id(),
            new TenantId(tenant.id()),
            tenant.slug(),
            saved.getUsername(),
            saved.getEmail(),
            cmd.roleCodes(),
            cmd.createdBy(),
            Instant.now()));

    log.info(
        "usuário criado id={} username={} roles={} criadoPor={}",
        saved.id().value(),
        saved.getUsername(),
        cmd.roleCodes(),
        cmd.createdBy() == null ? null : cmd.createdBy().value());

    return UserSummary.of(saved, cmd.roleCodes());
  }

  @Transactional(readOnly = true)
  public Page<UserSummary> list(Pageable pageable) {
    Page<UserAccount> page = users.findAllOrderedByUsername(pageable);
    if (page.isEmpty()) {
      return page.map(u -> UserSummary.of(u, List.of()));
    }
    return page.map(u -> UserSummary.of(u, resolveRoleCodesForUser(u.id().value())));
  }

  @Transactional(readOnly = true)
  public UserSummary findById(UserId userId) {
    UserAccount user = users.findById(userId.value()).orElseThrow(() -> notFound(userId));
    return UserSummary.of(user, resolveRoleCodesForUser(userId.value()));
  }

  @Transactional
  public UserSummary deactivate(UserId targetId, String reason, UserId actorId) {
    if (actorId != null && targetId.value().equals(actorId.value())) {
      throw new InvalidUserOperationException(
          "self-deactivate", "Usuário não pode desativar a própria conta.");
    }
    UserAccount user = users.findById(targetId.value()).orElseThrow(() -> notFound(targetId));
    if (user.getStatus() == UserStatus.DEACTIVATED) {
      throw new InvalidUserOperationException("already-deactivated", "Usuário já está desativado.");
    }
    List<String> targetRoles = resolveRoleCodesForUser(targetId.value());
    if (targetRoles.contains(OWNER_ROLE)) {
      assertOwnerCanBeDeactivated(actorId);
    }
    user.deactivate(reason);

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new UserDeactivatedEvent(
            targetId, new TenantId(tenant.id()), tenant.slug(), reason, actorId, Instant.now()));

    log.info(
        "usuário desativado id={} reason={} por={}",
        targetId.value(),
        reason,
        actorId == null ? null : actorId.value());
    return UserSummary.of(user, targetRoles);
  }

  @Transactional
  public UserSummary reactivate(UserId targetId, UserId actorId) {
    UserAccount user = users.findById(targetId.value()).orElseThrow(() -> notFound(targetId));
    if (user.getStatus() == UserStatus.DEACTIVATED) {
      throw new InvalidUserOperationException(
          "deactivated-cannot-reactivate",
          "Conta DEACTIVATED é final e não pode ser reativada via este endpoint.");
    }
    if (user.getStatus() == UserStatus.ACTIVE) {
      throw new InvalidUserOperationException("already-active", "Usuário já está ativo.");
    }
    user.reactivate();

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new UserReactivatedEvent(
            targetId, new TenantId(tenant.id()), tenant.slug(), actorId, Instant.now()));

    log.info(
        "usuário reativado id={} por={}",
        targetId.value(),
        actorId == null ? null : actorId.value());
    return UserSummary.of(user, resolveRoleCodesForUser(targetId.value()));
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  /**
   * Resolve roles por código preservando a ordem informada. Duplicados no input são silenciosamente
   * deduplicados. Qualquer código não encontrado lança {@link RoleNotFoundException} (a primeira
   * falha aborta — sem mensagem agregada por ora).
   */
  private List<Role> resolveRolesByCode(List<String> codes) {
    Set<String> uniqueCodes = new LinkedHashSet<>(codes);
    List<Role> resolved = new ArrayList<>(uniqueCodes.size());
    for (String code : uniqueCodes) {
      Role role = roles.findByCode(code).orElseThrow(() -> new RoleNotFoundException(code));
      resolved.add(role);
    }
    return resolved;
  }

  /**
   * Impede que um ator conceda permissões que não possui no estado atual do banco. Não confia nas
   * claims do JWT, que podem estar obsoletas após uma alteração de role.
   */
  private void validateRoleAssignments(List<Role> requestedRoles, UserId actorId) {
    if (actorId == null) {
      throw new RoleAssignmentForbiddenException("unknown");
    }
    Set<String> actorPermissions =
        new HashSet<>(rolePermissions.findPermissionCodesByUserId(actorId.value()));
    for (Role role : requestedRoles) {
      if (OWNER_ROLE.equals(role.getCode()) && !actorPermissions.contains(ALTER_PERMISSIONS)) {
        throw new RoleAssignmentForbiddenException(role.getCode());
      }
      Set<String> requestedPermissions =
          new HashSet<>(rolePermissions.findPermissionCodesByRoleId(role.getId()));
      if (!actorPermissions.containsAll(requestedPermissions)) {
        throw new RoleAssignmentForbiddenException(role.getCode());
      }
    }
  }

  private void assertOwnerCanBeDeactivated(UserId actorId) {
    if (actorId == null || !resolveRoleCodesForUser(actorId.value()).contains(OWNER_ROLE)) {
      throw new InvalidUserOperationException(
          "owner-hierarchy", "Apenas outro OWNER pode desativar uma conta OWNER.");
    }
    if (userRoles.countActiveUsersByRoleCode(OWNER_ROLE) <= 1) {
      throw new InvalidUserOperationException(
          "last-owner", "O último OWNER ativo do tenant não pode ser desativado.");
    }
  }

  private List<String> resolveRoleCodesForUser(UUID userId) {
    List<UUID> roleIds = userRoles.findRoleIdsByUserId(userId);
    if (roleIds.isEmpty()) return List.of();
    return roleIds.stream()
        .map(roles::findById)
        .flatMap(java.util.Optional::stream)
        .map(Role::getCode)
        .sorted()
        .toList();
  }

  private static UserNotFoundException notFound(UserId userId) {
    return new UserNotFoundException(userId);
  }
}
