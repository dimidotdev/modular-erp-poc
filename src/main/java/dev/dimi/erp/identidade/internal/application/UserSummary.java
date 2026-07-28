package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.domain.UserAccount;
import dev.dimi.erp.identidade.internal.domain.UserStatus;
import java.time.Instant;
import java.util.List;

/**
 * Projeção read-only de um {@link UserAccount} para uso no painel administrativo.
 *
 * <p>Inclui as roles do usuário (resolução já feita no service para evitar N+1 no controller) e
 * deliberadamente NÃO inclui {@code passwordHash}, {@code lastLoginIp} ou {@code
 * mustChangePassword} — campos sensíveis ou ruidosos para a listagem.
 */
public record UserSummary(
    UserId id,
    String username,
    String email,
    String fullName,
    UserStatus status,
    List<String> roles,
    Instant lastLoginAt,
    Instant createdAt,
    Instant deactivatedAt) {

  public static UserSummary of(UserAccount user, List<String> roles) {
    return new UserSummary(
        user.id(),
        user.getUsername(),
        user.getEmail(),
        user.getFullName(),
        user.getStatus(),
        roles,
        user.getLastLoginAt(),
        user.getCreatedAt(),
        user.getDeactivatedAt());
  }
}
