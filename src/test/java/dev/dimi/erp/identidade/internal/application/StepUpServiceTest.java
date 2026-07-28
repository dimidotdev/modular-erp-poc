package dev.dimi.erp.identidade.internal.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.persistence.RolePermissionAssignmentRepository;
import dev.dimi.erp.identidade.internal.persistence.RoleRepository;
import dev.dimi.erp.identidade.internal.persistence.TenantRepository;
import dev.dimi.erp.identidade.internal.persistence.UserAccountRepository;
import dev.dimi.erp.identidade.internal.persistence.UserRoleAssignmentRepository;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class StepUpServiceTest {

  @Mock private TenantRepository tenants;
  @Mock private UserAccountRepository users;
  @Mock private RoleRepository roles;
  @Mock private UserRoleAssignmentRepository userRoles;
  @Mock private RolePermissionAssignmentRepository rolePermissions;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenIssuer jwtIssuer;
  @Mock private LoginLockoutTracker lockoutTracker;
  @Mock private ApplicationEventPublisher events;
  @Mock private TransactionTemplate tx;

  private StepUpService service;
  private StepUpCommand command;

  @BeforeEach
  void setUp() {
    service =
        new StepUpService(
            tenants,
            users,
            roles,
            userRoles,
            rolePermissions,
            passwordEncoder,
            jwtIssuer,
            lockoutTracker,
            events,
            tx);
    command =
        new StepUpCommand(
            UserId.newId(),
            new TenantId(UUID.randomUUID()),
            "tenant-a",
            SessionId.newId(),
            "alice",
            "valid-password",
            "192.0.2.1",
            "Test Agent");
  }

  @Test
  void bloqueioExistenteInterrompeAntesDeConsultarTenant() {
    given(lockoutTracker.checkLockout("tenant-a", "alice", "192.0.2.1"))
        .willReturn(Optional.of(Duration.ofMinutes(15)));

    assertThatThrownBy(() -> service.stepUp(command)).isInstanceOf(AccountLockedException.class);

    verify(tenants, never()).findBySlug("tenant-a");
  }

  @Test
  void tenantInexistenteContaComoFalha() {
    given(lockoutTracker.checkLockout("tenant-a", "alice", "192.0.2.1"))
        .willReturn(Optional.empty());
    given(tenants.findBySlug("tenant-a")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.stepUp(command))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(lockoutTracker).recordFailure("tenant-a", "alice", "192.0.2.1");
  }
}
