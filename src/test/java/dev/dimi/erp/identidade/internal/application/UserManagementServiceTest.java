package dev.dimi.erp.identidade.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.dimi.erp.identidade.UserCreatedEvent;
import dev.dimi.erp.identidade.UserDeactivatedEvent;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.UserReactivatedEvent;
import dev.dimi.erp.identidade.internal.domain.Role;
import dev.dimi.erp.identidade.internal.domain.UserAccount;
import dev.dimi.erp.identidade.internal.domain.UserRoleAssignment;
import dev.dimi.erp.identidade.internal.persistence.RolePermissionAssignmentRepository;
import dev.dimi.erp.identidade.internal.persistence.RoleRepository;
import dev.dimi.erp.identidade.internal.persistence.UserAccountRepository;
import dev.dimi.erp.identidade.internal.persistence.UserRoleAssignmentRepository;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

  @Mock private UserAccountRepository users;
  @Mock private RoleRepository roles;
  @Mock private RolePermissionAssignmentRepository rolePermissions;
  @Mock private UserRoleAssignmentRepository userRoles;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ApplicationEventPublisher events;

  // Não-mock: política de senha é determinística e barata; vale a pena exercitar de verdade.
  private final PasswordPolicyValidator passwordPolicy = new PasswordPolicyValidator();

  private UserManagementService service;

  private static final String VALID_PASSWORD = "Tr0ub4dor&pivot-eclipse";
  private static final UserId ACTOR = UserId.newId();

  @BeforeEach
  void setUp() {
    service =
        new UserManagementService(
            users, roles, rolePermissions, userRoles, passwordPolicy, passwordEncoder, events);
    TenantContext.set(new Tenant(UUID.randomUUID(), "demo", "tenant_demo"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    void senhaForte_usernameLivre_emailLivre_rolesValidas_deveCriar() {
      given(users.existsByUsernameIgnoreCase("joao")).willReturn(false);
      given(users.existsByEmailIgnoreCase("joao@example.invalid")).willReturn(false);
      Role vendedor = Role.create("VENDEDOR", "Vendedor", "desc", true);
      given(roles.findByCode("VENDEDOR")).willReturn(Optional.of(vendedor));
      given(passwordEncoder.encode(VALID_PASSWORD)).willReturn("$argon2id$hash");
      given(users.save(any(UserAccount.class))).willAnswer(inv -> inv.getArgument(0));

      CreateUserCommand cmd =
          new CreateUserCommand(
              "joao",
              "joao@example.invalid",
              "João Almeida",
              VALID_PASSWORD,
              List.of("VENDEDOR"),
              true,
              ACTOR);

      UserSummary result = service.create(cmd);

      assertThat(result.username()).isEqualTo("joao");
      assertThat(result.email()).isEqualTo("joao@example.invalid");
      assertThat(result.roles()).containsExactly("VENDEDOR");
      verify(userRoles, times(1)).save(any(UserRoleAssignment.class));
      verify(passwordEncoder).encode(VALID_PASSWORD);
      verify(events).publishEvent(any(UserCreatedEvent.class));
    }

    @Test
    void senhaFraca_naoChegaNoRepository() {
      CreateUserCommand cmd =
          new CreateUserCommand(
              "joao",
              "joao@example.invalid",
              "João Almeida",
              "curta1!",
              List.of("VENDEDOR"),
              true,
              ACTOR);

      assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(WeakPasswordException.class);

      verify(users, never()).existsByUsernameIgnoreCase(anyString());
      verify(users, never()).save(any());
    }

    @Test
    void usernameDuplicado_lancaConflict() {
      given(users.existsByUsernameIgnoreCase("joao")).willReturn(true);

      CreateUserCommand cmd =
          new CreateUserCommand(
              "joao",
              "joao@example.invalid",
              "João Almeida",
              VALID_PASSWORD,
              List.of("VENDEDOR"),
              true,
              ACTOR);

      assertThatThrownBy(() -> service.create(cmd))
          .isInstanceOf(UserAlreadyExistsException.class)
          .hasFieldOrPropertyWithValue("code", "identidade.user-already-exists");

      verify(users, never()).save(any());
    }

    @Test
    void emailDuplicado_lancaConflict() {
      given(users.existsByUsernameIgnoreCase("joao")).willReturn(false);
      given(users.existsByEmailIgnoreCase("joao@example.invalid")).willReturn(true);

      CreateUserCommand cmd =
          new CreateUserCommand(
              "joao",
              "joao@example.invalid",
              "João Almeida",
              VALID_PASSWORD,
              List.of("VENDEDOR"),
              true,
              ACTOR);

      assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(UserAlreadyExistsException.class);

      verify(users, never()).save(any());
    }

    @Test
    void roleInexistente_lancaRoleNotFound() {
      given(users.existsByUsernameIgnoreCase("joao")).willReturn(false);
      given(users.existsByEmailIgnoreCase("joao@example.invalid")).willReturn(false);
      given(roles.findByCode("GHOST")).willReturn(Optional.empty());

      CreateUserCommand cmd =
          new CreateUserCommand(
              "joao",
              "joao@example.invalid",
              "João Almeida",
              VALID_PASSWORD,
              List.of("GHOST"),
              true,
              ACTOR);

      assertThatThrownBy(() -> service.create(cmd))
          .isInstanceOf(RoleNotFoundException.class)
          .hasFieldOrPropertyWithValue("code", "identidade.role-not-found");

      verify(users, never()).save(any());
    }

    @Test
    void atorSemPermissao_naoPodeAtribuirOwner() {
      Role owner = Role.create("OWNER", "Proprietário", "desc", true);
      given(roles.findByCode("OWNER")).willReturn(Optional.of(owner));
      given(rolePermissions.findPermissionCodesByUserId(ACTOR.value()))
          .willReturn(List.of("usuario.criar"));

      CreateUserCommand cmd =
          new CreateUserCommand(
              "joao",
              "joao@example.invalid",
              "João Almeida",
              VALID_PASSWORD,
              List.of("OWNER"),
              true,
              ACTOR);

      assertThatThrownBy(() -> service.create(cmd))
          .isInstanceOf(RoleAssignmentForbiddenException.class)
          .hasFieldOrPropertyWithValue("code", "identidade.role-assignment-forbidden");

      verify(users, never()).save(any());
    }
  }

  @Nested
  @DisplayName("deactivate")
  class Deactivate {

    @Test
    void atorEqualAlvo_recusaSelfDeactivate() {
      UserId target = ACTOR;
      assertThatThrownBy(() -> service.deactivate(target, "motivo qualquer", ACTOR))
          .isInstanceOf(InvalidUserOperationException.class)
          .hasFieldOrPropertyWithValue("code", "identidade.invalid-user-operation");

      verify(users, never()).findById(any());
    }

    @Test
    void usuarioInexistente_lancaNotFound() {
      UserId target = UserId.newId();
      given(users.findById(target.value())).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.deactivate(target, "motivo", ACTOR))
          .isInstanceOf(UserNotFoundException.class)
          .hasFieldOrPropertyWithValue("code", "identidade.user-not-found");
    }

    @Test
    void usuarioAtivo_devePassarParaDeactivated() {
      UserAccount user =
          UserAccount.register("alice", "alice@example.invalid", "Alice", "$argon2id$hash", false);
      UserId target = user.id();
      given(users.findById(target.value())).willReturn(Optional.of(user));
      given(userRoles.findRoleIdsByUserId(target.value())).willReturn(List.of());

      UserSummary result = service.deactivate(target, "ex-funcionária", ACTOR);

      assertThat(result.status())
          .isEqualTo(dev.dimi.erp.identidade.internal.domain.UserStatus.DEACTIVATED);
      assertThat(result.deactivatedAt()).isNotNull();
      verify(events).publishEvent(any(UserDeactivatedEvent.class));
    }

    @Test
    void atorSemOwner_naoPodeDesativarOwner() {
      UserAccount user =
          UserAccount.register("owner", "owner@example.invalid", "Owner", "$argon2id$hash", false);
      UserId target = user.id();
      Role owner = Role.create("OWNER", "Proprietário", "desc", true);
      given(users.findById(target.value())).willReturn(Optional.of(user));
      given(userRoles.findRoleIdsByUserId(target.value())).willReturn(List.of(owner.getId()));
      given(roles.findById(owner.getId())).willReturn(Optional.of(owner));
      given(userRoles.findRoleIdsByUserId(ACTOR.value())).willReturn(List.of());

      assertThatThrownBy(() -> service.deactivate(target, "motivo", ACTOR))
          .isInstanceOf(InvalidUserOperationException.class)
          .hasMessageContaining("Apenas outro OWNER");

      assertThat(user.canLogin()).isTrue();
    }

    @Test
    void ultimoOwnerNaoPodeSerDesativado() {
      UserAccount user =
          UserAccount.register("owner", "owner@example.invalid", "Owner", "$argon2id$hash", false);
      UserId target = user.id();
      Role owner = Role.create("OWNER", "Proprietário", "desc", true);
      given(users.findById(target.value())).willReturn(Optional.of(user));
      given(userRoles.findRoleIdsByUserId(target.value())).willReturn(List.of(owner.getId()));
      given(userRoles.findRoleIdsByUserId(ACTOR.value())).willReturn(List.of(owner.getId()));
      given(roles.findById(owner.getId())).willReturn(Optional.of(owner));
      given(userRoles.countActiveUsersByRoleCode("OWNER")).willReturn(1L);

      assertThatThrownBy(() -> service.deactivate(target, "motivo", ACTOR))
          .isInstanceOf(InvalidUserOperationException.class)
          .hasMessageContaining("último OWNER");

      assertThat(user.canLogin()).isTrue();
    }
  }

  @Nested
  @DisplayName("reactivate")
  class Reactivate {

    @Test
    void contaSuspendida_voltaParaActive() {
      UserAccount user =
          UserAccount.register("alice", "alice@example.invalid", "Alice", "$argon2id$hash", false);
      user.suspend();
      UserId target = user.id();
      given(users.findById(target.value())).willReturn(Optional.of(user));
      given(userRoles.findRoleIdsByUserId(target.value())).willReturn(List.of());

      UserSummary result = service.reactivate(target, ACTOR);

      assertThat(result.status())
          .isEqualTo(dev.dimi.erp.identidade.internal.domain.UserStatus.ACTIVE);
      verify(events).publishEvent(any(UserReactivatedEvent.class));
    }

    @Test
    void contaJaAtiva_naoFazNada_eRecusa() {
      UserAccount user =
          UserAccount.register("alice", "alice@example.invalid", "Alice", "$argon2id$hash", false);
      UserId target = user.id();
      given(users.findById(target.value())).willReturn(Optional.of(user));

      assertThatThrownBy(() -> service.reactivate(target, ACTOR))
          .isInstanceOf(InvalidUserOperationException.class);
    }

    @Test
    void contaDeactivated_naoPodeSerReativada() {
      UserAccount user =
          UserAccount.register("alice", "alice@example.invalid", "Alice", "$argon2id$hash", false);
      user.deactivate("ex");
      UserId target = user.id();
      given(users.findById(target.value())).willReturn(Optional.of(user));

      assertThatThrownBy(() -> service.reactivate(target, ACTOR))
          .isInstanceOf(InvalidUserOperationException.class)
          .hasFieldOrPropertyWithValue("code", "identidade.invalid-user-operation");
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void existente_devolveSummary() {
      UserAccount user =
          UserAccount.register("alice", "alice@example.invalid", "Alice", "$argon2id$hash", false);
      UserId target = user.id();
      given(users.findById(target.value())).willReturn(Optional.of(user));
      given(userRoles.findRoleIdsByUserId(target.value())).willReturn(List.of());

      UserSummary result = service.findById(target);

      assertThat(result.username()).isEqualTo("alice");
    }

    @Test
    void inexistente_lancaNotFound() {
      UserId target = UserId.newId();
      given(users.findById(target.value())).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.findById(target)).isInstanceOf(UserNotFoundException.class);
    }
  }
}
