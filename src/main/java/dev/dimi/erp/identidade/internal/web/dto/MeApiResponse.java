package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.util.List;

/**
 * Response do {@code GET /api/v1/auth/me}.
 *
 * <p>Reflete o que o backend já conhece do principal autenticado a partir do JWT — sem tocar no
 * banco. Útil pro front (a) repopular store após reload, (b) descobrir permissões antes de
 * renderizar menus.
 */
public record MeApiResponse(
    UserId userId,
    String username,
    TenantId tenantId,
    String tenantSlug,
    SessionId sessionId,
    List<String> roles,
    List<String> permissions) {

  public static MeApiResponse from(AuthenticatedUser user) {
    return new MeApiResponse(
        user.userId(),
        user.username(),
        user.tenantId(),
        user.tenantSlug(),
        user.sessionId(),
        user.roles().stream().sorted().toList(),
        user.permissions().stream().sorted().toList());
  }
}
