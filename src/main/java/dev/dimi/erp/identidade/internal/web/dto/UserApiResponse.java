package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.application.UserSummary;
import dev.dimi.erp.identidade.internal.domain.UserStatus;
import java.time.Instant;
import java.util.List;

/** Response de um único usuário em endpoints administrativos. */
public record UserApiResponse(
    UserId id,
    String username,
    String email,
    String fullName,
    UserStatus status,
    List<String> roles,
    Instant lastLoginAt,
    Instant createdAt,
    Instant deactivatedAt) {

  public static UserApiResponse from(UserSummary s) {
    return new UserApiResponse(
        s.id(),
        s.username(),
        s.email(),
        s.fullName(),
        s.status(),
        s.roles(),
        s.lastLoginAt(),
        s.createdAt(),
        s.deactivatedAt());
  }
}
